package com.ticketmaster.payment.domain.model;

import com.ticketmaster.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction – Aggregate Root của bounded context Payment.
 *
 * <p>Đại diện cho một lần thanh toán từ đầu đến cuối:
 * PENDING → PROCESSING → SUCCEEDED | FAILED | CANCELLED
 * SUCCEEDED → REFUNDED (khi refund)
 *
 * <p><b>Business Invariants:</b>
 * <ul>
 *   <li>Mỗi bookingId chỉ có tối đa 1 SUCCEEDED transaction (idempotency)</li>
 *   <li>Không thể refund transaction chưa SUCCEEDED</li>
 *   <li>amount phải > 0</li>
 *   <li>Idempotency key = bookingId → tránh double-charge nếu Kafka retry</li>
 * </ul>
 *
 * <p><b>Stripe Integration:</b>
 * <ul>
 *   <li>{@code stripePaymentIntentId} = ID của Stripe PaymentIntent</li>
 *   <li>{@code stripeChargeId} = ID charge sau khi succeed (dùng để refund)</li>
 *   <li>{@code failureCode} / {@code failureMessage} = error từ Stripe khi thất bại</li>
 * </ul>
 *
 * <p><b>Pure Java:</b> Không có annotation Spring hay JPA.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    /** UUID v4 – primary key, sinh bởi application. */
    private String id;

    /**
     * ID của Booking liên kết (từ booking-service).
     * Dùng làm idempotency key khi gọi Stripe API.
     */
    private String bookingId;

    /** ID của user thực hiện thanh toán. */
    private String userId;

    /** Email user (snapshot để gửi notification). */
    private String userEmail;

    /** Số tiền cần thanh toán (phải > 0). */
    private BigDecimal amount;

    /** Đơn vị tiền tệ (ISO 4217: "USD", "VND", v.v.). */
    private String currency;

    /** Phương thức thanh toán (Value Object). */
    private PaymentMethod paymentMethod;

    /** Trạng thái hiện tại của transaction. */
    private TransactionStatus status;

    /**
     * Stripe PaymentIntent ID (vd: {@code pi_3OxxxxxxxxxxxYYY}).
     * Được set khi tạo PaymentIntent trên Stripe.
     */
    private String stripePaymentIntentId;

    /**
     * Stripe Charge ID (vd: {@code ch_3OxxxxxxxxxxxYYY}).
     * Được set sau khi charge thành công. Dùng để refund.
     */
    private String stripeChargeId;

    /**
     * Stripe Refund ID (vd: {@code re_3OxxxxxxxxxxxYYY}).
     * Được set sau khi refund thành công.
     */
    private String stripeRefundId;

    /** Mã lỗi Stripe (vd: "card_declined"). Null khi không có lỗi. */
    private String failureCode;

    /** Mô tả lỗi human-readable từ Stripe. Null khi không có lỗi. */
    private String failureMessage;

    /** Thời điểm tạo (UTC). */
    private Instant createdAt;

    /** Thời điểm cập nhật (UTC). */
    private Instant updatedAt;

    // ── Factory Method ─────────────────────────────────────────────

    /**
     * Tạo Transaction mới khi nhận {@code booking.created} từ Kafka.
     *
     * @param id          UUID đã sinh sẵn
     * @param bookingId   ID booking (cũng là idempotency key cho Stripe)
     * @param userId      ID user
     * @param userEmail   email user
     * @param amount      số tiền cần thanh toán
     * @param currency    đơn vị tiền tệ
     * @param paymentMethod phương thức thanh toán
     * @return Transaction mới với status=PENDING
     */
    public static Transaction create(String id, String bookingId, String userId,
                                     String userEmail, BigDecimal amount, String currency,
                                     PaymentMethod paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Payment amount must be greater than 0",
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST);
        }
        Instant now = Instant.now();
        return Transaction.builder()
                .id(id)
                .bookingId(bookingId)
                .userId(userId)
                .userEmail(userEmail)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .status(TransactionStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ── State Transition Methods ────────────────────────────────────

    /**
     * Chuyển sang PROCESSING – đã gửi request đến Stripe.
     *
     * @param stripePaymentIntentId Stripe PaymentIntent ID vừa tạo
     */
    public Transaction startProcessing(String stripePaymentIntentId) {
        validateTransition(TransactionStatus.PENDING, TransactionStatus.PROCESSING);
        return toBuilder()
                .status(TransactionStatus.PROCESSING)
                .stripePaymentIntentId(stripePaymentIntentId)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Chuyển sang SUCCEEDED – Stripe charge thành công.
     *
     * @param stripeChargeId Stripe Charge ID để dùng khi refund
     */
    public Transaction succeed(String stripeChargeId) {
        validateTransition(TransactionStatus.PROCESSING, TransactionStatus.SUCCEEDED);
        return toBuilder()
                .status(TransactionStatus.SUCCEEDED)
                .stripeChargeId(stripeChargeId)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Chuyển sang FAILED – Stripe từ chối charge.
     *
     * @param failureCode    mã lỗi Stripe (vd: "card_declined")
     * @param failureMessage mô tả lỗi
     */
    public Transaction fail(String failureCode, String failureMessage) {
        if (this.status != TransactionStatus.PROCESSING
                && this.status != TransactionStatus.PENDING) {
            throw new BusinessException(
                    "Cannot fail transaction with status: " + this.status,
                    "INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT);
        }
        return toBuilder()
                .status(TransactionStatus.FAILED)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Chuyển sang CANCELLED – booking hết hạn trước khi xử lý xong.
     */
    public Transaction cancel() {
        if (this.status == TransactionStatus.SUCCEEDED
                || this.status == TransactionStatus.REFUNDED) {
            throw new BusinessException(
                    "Cannot cancel completed transaction",
                    "CANNOT_CANCEL_COMPLETED", HttpStatus.CONFLICT);
        }
        return toBuilder()
                .status(TransactionStatus.CANCELLED)
                .failureMessage("Cancelled – booking expired")
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Chuyển sang REFUNDED – Stripe refund thành công.
     *
     * @param stripeRefundId Stripe Refund ID
     * @throws BusinessException nếu transaction chưa SUCCEEDED
     */
    public Transaction refund(String stripeRefundId) {
        if (this.status != TransactionStatus.SUCCEEDED) {
            throw new BusinessException(
                    "Can only refund SUCCEEDED transactions",
                    "INVALID_REFUND", HttpStatus.CONFLICT);
        }
        return toBuilder()
                .status(TransactionStatus.REFUNDED)
                .stripeRefundId(stripeRefundId)
                .updatedAt(Instant.now())
                .build();
    }

    // ── Query Methods ──────────────────────────────────────────────

    public boolean isSucceeded()   { return TransactionStatus.SUCCEEDED.equals(status); }
    public boolean isFailed()      { return TransactionStatus.FAILED.equals(status); }
    public boolean isPending()     { return TransactionStatus.PENDING.equals(status); }
    public boolean isProcessing()  { return TransactionStatus.PROCESSING.equals(status); }
    public boolean isRefunded()    { return TransactionStatus.REFUNDED.equals(status); }

    /**
     * Số tiền tính bằng cents (Stripe API yêu cầu integer cents).
     * VD: $10.50 → 1050
     */
    public long getAmountInCents() {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }

    // ── Validation Helper ──────────────────────────────────────────

    private void validateTransition(TransactionStatus from, TransactionStatus to) {
        if (this.status != from) {
            throw new BusinessException(
                    String.format("Cannot transition from %s to %s (current: %s)",
                            from, to, this.status),
                    "INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT);
        }
    }

    // ── Builder Helper ─────────────────────────────────────────────

    private TransactionBuilder toBuilder() {
        return Transaction.builder()
                .id(id).bookingId(bookingId).userId(userId).userEmail(userEmail)
                .amount(amount).currency(currency).paymentMethod(paymentMethod)
                .status(status)
                .stripePaymentIntentId(stripePaymentIntentId)
                .stripeChargeId(stripeChargeId)
                .stripeRefundId(stripeRefundId)
                .failureCode(failureCode).failureMessage(failureMessage)
                .createdAt(createdAt);
    }
}