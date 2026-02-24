package com.ticketmaster.payment.application.handler;

import com.ticketmaster.common.util.IdGenerator;
import com.ticketmaster.payment.application.command.ProcessPaymentCommand;
import com.ticketmaster.payment.application.kafka.PaymentEventProducer;
import com.ticketmaster.payment.application.port.PaymentGatewayPort;
import com.ticketmaster.payment.domain.model.PaymentMethod;
import com.ticketmaster.payment.domain.model.Transaction;
import com.ticketmaster.payment.domain.repository.TransactionRepository;
import com.ticketmaster.payment.domain.service.PaymentDomainService;
import com.ticketmaster.payment.infrastructure.gateway.PaymentGatewayException;
import com.ticketmaster.payment.interfaces.dto.TransactionResponse;
import com.ticketmaster.payment.infrastructure.persistence.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handler cho use case xử lý thanh toán qua Stripe.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>Idempotency check – tránh double-charge khi Kafka retry</li>
 *   <li>Validate booking chưa hết hạn</li>
 *   <li>Validate amount > 0</li>
 *   <li>Tạo Transaction với status=PENDING, lưu DB</li>
 *   <li>Chuyển sang PROCESSING, lưu DB (cho phép detect stuck transactions)</li>
 *   <li>Gọi {@link PaymentGatewayPort#charge} (Stripe) – wrapped bởi CircuitBreaker + Retry</li>
 *   <li>Nếu thành công: SUCCEEDED → publish {@code payment.processed}</li>
 *   <li>Nếu thất bại: FAILED → publish {@code payment.failed}</li>
 * </ol>
 *
 * <p><b>CircuitBreaker:</b> Được apply ở {@code StripePaymentAdapter}, không ở đây.
 * Handler chỉ xử lý exception từ port.
 *
 * <p><b>Retry trong DB transaction:</b>
 * DB transaction bao quanh toàn bộ flow để đảm bảo consistency.
 * Nếu DB save thất bại sau khi Stripe charge thành công → đây là trường hợp
 * "đã charge nhưng chưa lưu được" → cần manual reconciliation.
 * Trong production nên dùng Outbox pattern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessPaymentHandler {

    private final PaymentDomainService  paymentDomainService;
    private final TransactionRepository transactionRepository;
    private final PaymentGatewayPort    paymentGatewayPort;
    private final PaymentEventProducer  paymentEventProducer;
    private final TransactionMapper     transactionMapper;

    @Transactional
    public TransactionResponse handle(ProcessPaymentCommand command) {
        log.info("[PAYMENT] Processing payment | bookingId={} amount={} {}",
                command.getBookingId(), command.getAmount(), command.getCurrency());

        // 1. Idempotency check – skip nếu đã có SUCCEEDED transaction
        paymentDomainService.validateNoDuplicateTransaction(command.getBookingId());

        // 2. Validate booking chưa hết hạn
        if (command.getBookingExpiresAt() != null
                && Instant.now().isAfter(command.getBookingExpiresAt())) {
            log.warn("[PAYMENT] Booking {} already expired at {}",
                    command.getBookingId(), command.getBookingExpiresAt());
            // Không throw – chỉ log, booking-service Quartz job sẽ xử lý expire
            return null;
        }

        // 3. Validate amount
        paymentDomainService.validateAmount(command.getAmount());

        // 4. Retrieve PaymentMethod từ Stripe để lấy thông tin card
        PaymentMethod paymentMethod;
        try {
            paymentMethod = paymentGatewayPort.retrievePaymentMethod(command.getPaymentMethodId());
        } catch (Exception e) {
            log.warn("[PAYMENT] Could not retrieve payment method {}: {} – using minimal info",
                    command.getPaymentMethodId(), e.getMessage());
            // Fallback: tạo PaymentMethod với thông tin tối thiểu
            paymentMethod = PaymentMethod.builder()
                    .stripePaymentMethodId(command.getPaymentMethodId())
                    .type("card")
                    .build();
        }

        // 5. Tạo Transaction domain object và lưu DB (PENDING)
        Transaction transaction = Transaction.create(
                IdGenerator.newId(),
                command.getBookingId(),
                command.getUserId(),
                command.getUserEmail(),
                command.getAmount(),
                command.getCurrency() != null ? command.getCurrency() : "USD",
                paymentMethod
        );
        transaction = transactionRepository.save(transaction);
        log.info("[PAYMENT] Transaction created id={} status=PENDING", transaction.getId());

        // 6. Chuyển sang PROCESSING và lưu DB (đánh dấu đang gửi đến Stripe)
        // Nếu service crash sau bước này → có thể detect "stuck PROCESSING" và reconcile
        Transaction processing = transaction.startProcessing("pending-stripe-call");
        processing = transactionRepository.save(processing);

        // 7. Gọi Stripe (via Port – CircuitBreaker + Retry được apply ở StripePaymentAdapter)
        try {
            PaymentGatewayPort.ChargeResult chargeResult = paymentGatewayPort.charge(
                    processing,
                    command.getPaymentMethodId(),
                    command.getBookingId()  // idempotencyKey = bookingId
            );

            if (chargeResult.succeeded()) {
                // 8a. SUCCESS: PROCESSING → SUCCEEDED
                Transaction succeeded = processing.succeed(chargeResult.chargeId());
                // Update paymentIntentId đúng từ Stripe
                succeeded = Transaction.builder()
                        .id(succeeded.getId()).bookingId(succeeded.getBookingId())
                        .userId(succeeded.getUserId()).userEmail(succeeded.getUserEmail())
                        .amount(succeeded.getAmount()).currency(succeeded.getCurrency())
                        .paymentMethod(succeeded.getPaymentMethod())
                        .status(succeeded.getStatus())
                        .stripePaymentIntentId(chargeResult.paymentIntentId())
                        .stripeChargeId(chargeResult.chargeId())
                        .createdAt(succeeded.getCreatedAt())
                        .updatedAt(succeeded.getUpdatedAt())
                        .build();
                Transaction saved = transactionRepository.save(succeeded);

                // 9a. Publish payment.processed → booking-service CONFIRM
                paymentEventProducer.publishPaymentProcessed(saved);
                log.info("[PAYMENT] ✅ Payment SUCCEEDED | transactionId={} bookingId={} chargeId={}",
                        saved.getId(), saved.getBookingId(), saved.getStripeChargeId());

                return transactionMapper.toResponse(saved);

            } else {
                // 8b. FAILED by Stripe (e.g. card_declined)
                Transaction failed = processing.fail(
                        chargeResult.failureCode(), chargeResult.failureMessage());
                Transaction saved = transactionRepository.save(failed);

                // 9b. Publish payment.failed → booking-service CANCEL
                paymentEventProducer.publishPaymentFailed(saved, command.getUserEmail(),
                        chargeResult.failureCode(), chargeResult.failureMessage());
                log.warn("[PAYMENT] ❌ Payment FAILED | transactionId={} bookingId={} code={}",
                        saved.getId(), saved.getBookingId(), chargeResult.failureCode());

                return transactionMapper.toResponse(saved);
            }

        } catch (PaymentGatewayException e) {
            // Circuit breaker open hoặc network error
            log.error("[PAYMENT] Gateway exception for bookingId={}: {}",
                    command.getBookingId(), e.getMessage(), e);

            Transaction failed = processing.fail("gateway_error", e.getMessage());
            Transaction saved = transactionRepository.save(failed);

            paymentEventProducer.publishPaymentFailed(saved, command.getUserEmail(),
                    "gateway_error", "Payment gateway temporarily unavailable");

            return transactionMapper.toResponse(saved);

        } catch (Exception e) {
            // Unexpected error
            log.error("[PAYMENT] Unexpected error for bookingId={}: {}",
                    command.getBookingId(), e.getMessage(), e);

            Transaction failed = processing.fail("internal_error", e.getMessage());
            transactionRepository.save(failed);

            paymentEventProducer.publishPaymentFailed(processing, command.getUserEmail(),
                    "internal_error", "Internal payment processing error");

            throw e; // Re-throw để Kafka không ACK → retry
        }
    }
}