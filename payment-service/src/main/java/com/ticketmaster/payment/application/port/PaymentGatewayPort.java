package com.ticketmaster.payment.application.port;

import com.ticketmaster.payment.domain.model.PaymentMethod;
import com.ticketmaster.payment.domain.model.Transaction;

import java.math.BigDecimal;

/**
 * <b>Outbound Port</b> – Interface định nghĩa contract cho payment gateway.
 * Đây là trái tim của Hexagonal Architecture trong payment-service.
 *
 * <p><b>Tại sao dùng Port interface thay vì gọi Stripe trực tiếp?</b>
 * <ul>
 *   <li><b>Testability:</b> Mock dễ dàng trong unit test, không cần Stripe sandbox</li>
 *   <li><b>Swap gateway:</b> Có thể thay Stripe bằng PayPal/VNPay/MoMo
 *       mà không sửa domain logic</li>
 *   <li><b>Abstraction:</b> Domain không biết Stripe tồn tại</li>
 *   <li><b>Circuit Breaker:</b> Resilience4j wrap ở adapter level, không ảnh hưởng domain</li>
 * </ul>
 *
 * <p><b>Implementation:</b>
 * {@link com.ticketmaster.payment.infrastructure.gateway.StripePaymentAdapter}
 *
 * <p><b>Method contract:</b>
 * <ul>
 *   <li>Mọi method đều có thể throw {@code PaymentGatewayException}</li>
 *   <li>Idempotency được handle ở adapter level (truyền idempotencyKey)</li>
 *   <li>CircuitBreaker và Retry annotation ở adapter, không ở đây</li>
 * </ul>
 */
public interface PaymentGatewayPort {

    /**
     * Kết quả của một lần charge.
     */
    record ChargeResult(
            /** Stripe PaymentIntent ID: {@code pi_xxx} */
            String paymentIntentId,
            /** Stripe Charge ID: {@code ch_xxx} – dùng để refund */
            String chargeId,
            /** true = thành công */
            boolean succeeded,
            /** Mã lỗi Stripe khi thất bại (vd: "card_declined") */
            String failureCode,
            /** Mô tả lỗi human-readable */
            String failureMessage
    ) {}

    /**
     * Kết quả của một lần refund.
     */
    record RefundResult(
            /** Stripe Refund ID: {@code re_xxx} */
            String refundId,
            /** true = thành công */
            boolean succeeded,
            /** Mô tả lỗi nếu refund thất bại */
            String failureMessage
    ) {}

    /**
     * Thực hiện charge (thu tiền) từ payment method.
     *
     * <p>Stripe flow: Create PaymentIntent → Confirm → get Charge ID.
     *
     * @param transaction     Transaction domain object chứa amount, currency, bookingId
     * @param paymentMethodId Stripe Payment Method ID (vd: {@code pm_xxx})
     * @param idempotencyKey  Key để Stripe deduplicate (dùng bookingId)
     * @return {@link ChargeResult} với paymentIntentId và chargeId khi thành công
     * @throws com.ticketmaster.payment.infrastructure.gateway.PaymentGatewayException nếu lỗi network/Stripe API
     */
    ChargeResult charge(Transaction transaction, String paymentMethodId, String idempotencyKey);

    /**
     * Thực hiện refund cho một charge đã thành công.
     *
     * @param chargeId       Stripe Charge ID (từ {@link ChargeResult#chargeId()})
     * @param amount         Số tiền refund (null = refund toàn bộ)
     * @param idempotencyKey Key để Stripe deduplicate
     * @return {@link RefundResult}
     */
    RefundResult refund(String chargeId, BigDecimal amount, String idempotencyKey);

    /**
     * Validate payment method còn hợp lệ không (optional pre-check).
     *
     * @param paymentMethodId Stripe Payment Method ID
     * @return {@link PaymentMethod} với thông tin chi tiết (brand, last4, exp)
     * @throws com.ticketmaster.payment.infrastructure.gateway.PaymentGatewayException nếu payment method không tồn tại
     */
    PaymentMethod retrievePaymentMethod(String paymentMethodId);
}