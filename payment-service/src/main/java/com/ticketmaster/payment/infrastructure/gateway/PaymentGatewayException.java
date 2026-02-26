package com.ticketmaster.payment.infrastructure.gateway;

/**
 * Exception được ném bởi {@link StripePaymentAdapter} khi gọi Stripe API thất bại.
 *
 * <p>Phân loại lỗi:
 * <ul>
 *   <li>{@code NETWORK_ERROR}     – timeout, connection refused</li>
 *   <li>{@code CIRCUIT_BREAKER}   – Resilience4j circuit breaker open</li>
 *   <li>{@code STRIPE_API_ERROR}  – Stripe trả về lỗi không phải card decline</li>
 * </ul>
 *
 * <p>Lưu ý: Card decline KHÔNG throw exception này – thay vào đó trả về
 * {@link com.ticketmaster.payment.application.port.PaymentGatewayPort.ChargeResult}
 * với {@code succeeded=false}.
 *
 * <p>Exception này sẽ được bắt ở {@link com.ticketmaster.payment.application.handler.ProcessPaymentHandler}
 * để publish {@code payment.failed} event.
 */
public class PaymentGatewayException extends RuntimeException {

    private final String errorType;

    public PaymentGatewayException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    public PaymentGatewayException(String message, String errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }

    // ── Factory Methods ──────────────────────────────────────────

    public static PaymentGatewayException networkError(String detail, Throwable cause) {
        return new PaymentGatewayException(
                "Stripe network error: " + detail, "NETWORK_ERROR", cause);
    }

    public static PaymentGatewayException circuitBreakerOpen() {
        return new PaymentGatewayException(
                "Payment gateway circuit breaker is OPEN – Stripe temporarily unavailable",
                "CIRCUIT_BREAKER");
    }

    public static PaymentGatewayException stripeApiError(String detail, Throwable cause) {
        return new PaymentGatewayException(
                "Stripe API error: " + detail, "STRIPE_API_ERROR", cause);
    }
}

