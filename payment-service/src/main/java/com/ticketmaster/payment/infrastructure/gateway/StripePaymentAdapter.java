package com.ticketmaster.payment.infrastructure.gateway;

import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.ticketmaster.payment.application.port.PaymentGatewayPort;
import com.ticketmaster.payment.domain.model.PaymentMethod;
import com.ticketmaster.payment.domain.model.Transaction;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe implementation của {@link PaymentGatewayPort} (Outbound Adapter).
 *
 * <p><b>Stripe Flow:</b>
 * <ol>
 *   <li>Create PaymentIntent với amount, currency, payment_method, idempotency_key</li>
 *   <li>Confirm PaymentIntent → Stripe charges the card immediately</li>
 *   <li>Extract Charge ID từ latest_charge để dùng khi refund</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> Truyền {@code bookingId} làm idempotency key cho cả
 * Create và Confirm – nếu Stripe đã nhận request này trước, trả về kết quả cũ.
 *
 * <p><b>Card decline vs Network error:</b>
 * <ul>
 *   <li>CardException (card_declined, etc.) → return {@code ChargeResult(succeeded=false)}</li>
 *   <li>IOException/SocketTimeout → Retry, sau đó throw {@link PaymentGatewayException}</li>
 *   <li>Other StripeException → throw {@link PaymentGatewayException}</li>
 * </ul>
 *
 * <p><b>Resilience4j annotations:</b> CircuitBreaker + Retry applied at method level.
 * Instance name {@code "stripe-gateway"} phải khớp với config trong application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentAdapter implements PaymentGatewayPort {

    private static final String CB_INSTANCE    = "stripe-gateway";
    private static final String CURRENCY_USD   = "usd";

    // ── charge ──────────────────────────────────────────────────

    @Override
    @CircuitBreaker(name = CB_INSTANCE, fallbackMethod = "chargeFallback")
    @Retry(name = CB_INSTANCE)
    public ChargeResult charge(Transaction transaction, String paymentMethodId,
                               String idempotencyKey) {

        log.debug("[STRIPE] Creating PaymentIntent | bookingId={} amount={} {}",
                transaction.getBookingId(), transaction.getAmount(), transaction.getCurrency());

        try {
            // Metadata để trace booking trong Stripe dashboard
            Map<String, String> metadata = new HashMap<>();
            metadata.put("booking_id",   transaction.getBookingId());
            metadata.put("user_id",      transaction.getUserId());
            metadata.put("user_email",   transaction.getUserEmail());
            metadata.put("transaction_id", transaction.getId());

            String currency = transaction.getCurrency() != null
                    ? transaction.getCurrency().toLowerCase()
                    : CURRENCY_USD;

            // Step 1: Create PaymentIntent
            PaymentIntentCreateParams createParams = PaymentIntentCreateParams.builder()
                    .setAmount(transaction.getAmountInCents())
                    .setCurrency(currency)
                    .setPaymentMethod(paymentMethodId)
                    .setConfirm(true)              // Confirm immediately
                    .setErrorOnRequiresAction(true) // Fail if 3DS required (no redirect flow)
                    .setDescription("Ticketmaster – " + transaction.getBookingId())
                    .putAllMetadata(metadata)
                    .build();

            // Stripe idempotency key: bookingId ensures no double-charge on Kafka retry
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey("charge-" + idempotencyKey)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(createParams, requestOptions);

            String intentId = paymentIntent.getId();
            String status   = paymentIntent.getStatus();

            log.info("[STRIPE] PaymentIntent {} | status={} | bookingId={}",
                    intentId, status, transaction.getBookingId());

            if ("succeeded".equals(status)) {
                // Extract Charge ID từ latest_charge
                String chargeId = null;
                if (paymentIntent.getLatestCharge() != null) {
                    chargeId = paymentIntent.getLatestCharge();
                }

                log.info("[STRIPE] ✅ Charge succeeded | intentId={} chargeId={} bookingId={}",
                        intentId, chargeId, transaction.getBookingId());

                return new ChargeResult(intentId, chargeId, true, null, null);

            } else {
                // PaymentIntent created but not succeeded (e.g. requires_action)
                log.warn("[STRIPE] PaymentIntent not succeeded | status={} intentId={} bookingId={}",
                        status, intentId, transaction.getBookingId());

                return new ChargeResult(intentId, null, false,
                        "intent_not_succeeded",
                        "PaymentIntent status: " + status);
            }

        } catch (CardException e) {
            // Card decline – EXPECTED failure, không retry, không circuit break
            log.warn("[STRIPE] Card declined | code={} declineCode={} bookingId={}",
                    e.getCode(), e.getDeclineCode(), transaction.getBookingId());

            return new ChargeResult(null, null, false, e.getCode(),
                    e.getUserMessage() != null ? e.getUserMessage() : e.getMessage());

        } catch (StripeException e) {
            log.error("[STRIPE] API error | code={} requestId={} bookingId={}",
                    e.getCode(), e.getRequestId(), transaction.getBookingId(), e);
            throw PaymentGatewayException.stripeApiError(e.getMessage(), e);

        } catch (Exception e) {
            log.error("[STRIPE] Unexpected error | bookingId={}", transaction.getBookingId(), e);
            throw PaymentGatewayException.networkError(e.getMessage(), e);
        }
    }

    /**
     * Fallback khi CircuitBreaker OPEN – fail fast, không gọi Stripe.
     */
    @SuppressWarnings("unused")
    private ChargeResult chargeFallback(Transaction transaction, String paymentMethodId,
                                        String idempotencyKey, Throwable t) {
        log.warn("[STRIPE] Circuit breaker OPEN – fallback for bookingId={}",
                transaction.getBookingId());
        throw PaymentGatewayException.circuitBreakerOpen();
    }

    // ── refund ──────────────────────────────────────────────────

    @Override
    @CircuitBreaker(name = CB_INSTANCE, fallbackMethod = "refundFallback")
    @Retry(name = CB_INSTANCE)
    public RefundResult refund(String chargeId, BigDecimal amount, String idempotencyKey) {
        log.debug("[STRIPE] Creating refund | chargeId={} amount={}", chargeId, amount);

        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setCharge(chargeId);

            if (amount != null) {
                long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
                paramsBuilder.setAmount(amountInCents);
            }

            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey("refund-" + idempotencyKey)
                    .build();

            Refund refund = Refund.create(paramsBuilder.build(), requestOptions);

            if ("succeeded".equals(refund.getStatus())) {
                log.info("[STRIPE] ✅ Refund succeeded | refundId={} chargeId={}",
                        refund.getId(), chargeId);
                return new RefundResult(refund.getId(), true, null);
            } else {
                log.warn("[STRIPE] Refund not succeeded | status={} refundId={}",
                        refund.getStatus(), refund.getId());
                return new RefundResult(refund.getId(), false,
                        "Refund status: " + refund.getStatus());
            }

        } catch (StripeException e) {
            log.error("[STRIPE] Refund API error | chargeId={} code={}", chargeId, e.getCode(), e);
            throw PaymentGatewayException.stripeApiError(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private RefundResult refundFallback(String chargeId, BigDecimal amount,
                                        String idempotencyKey, Throwable t) {
        log.warn("[STRIPE] Circuit breaker OPEN – refund fallback for chargeId={}", chargeId);
        throw PaymentGatewayException.circuitBreakerOpen();
    }

    // ── retrievePaymentMethod ────────────────────────────────────

    @Override
    @CircuitBreaker(name = CB_INSTANCE, fallbackMethod = "retrievePmFallback")
    public PaymentMethod retrievePaymentMethod(String paymentMethodId) {
        log.debug("[STRIPE] Retrieving payment method | pmId={}", paymentMethodId);

        try {
            com.stripe.model.PaymentMethod pm =
                    com.stripe.model.PaymentMethod.retrieve(paymentMethodId);

            if (pm.getCard() != null) {
                com.stripe.model.PaymentMethod.Card card = pm.getCard();
                return PaymentMethod.card(
                        pm.getId(),
                        card.getBrand(),
                        card.getLast4(),
                        card.getExpMonth().intValue(),
                        card.getExpYear().intValue()
                );
            }

            return PaymentMethod.builder()
                    .stripePaymentMethodId(pm.getId())
                    .type(pm.getType())
                    .build();

        } catch (StripeException e) {
            log.warn("[STRIPE] Cannot retrieve PM {} – {}", paymentMethodId, e.getMessage());
            throw PaymentGatewayException.stripeApiError(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PaymentMethod retrievePmFallback(String paymentMethodId, Throwable t) {
        log.warn("[STRIPE] Circuit breaker OPEN – PM retrieve fallback for {}", paymentMethodId);
        throw PaymentGatewayException.circuitBreakerOpen();
    }
}

