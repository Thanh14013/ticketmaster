package com.ticketmaster.payment.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO cho Transaction aggregate.
 *
 * <p>Không expose sensitive payment data (full card number, Stripe secret).
 * Chỉ expose last4, brand để hiển thị lịch sử thanh toán.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    /** Internal transaction ID trong payment_db. */
    private final String     id;

    /** Booking ID liên kết. */
    private final String     bookingId;

    /** User ID thực hiện thanh toán. */
    private final String     userId;

    /** Số tiền đã thanh toán. */
    private final BigDecimal amount;

    /** Đơn vị tiền tệ (ISO 4217). */
    private final String     currency;

    /**
     * Trạng thái giao dịch.
     * Possible values: PENDING | PROCESSING | SUCCEEDED | FAILED | CANCELLED | REFUNDED
     */
    private final String     status;

    /**
     * Mô tả phương thức thanh toán human-readable.
     * Ví dụ: "Visa •••• 4242 (12/26)"
     */
    private final String     paymentMethodDisplay;

    /** Stripe PaymentIntent ID (for reference only). */
    private final String     stripePaymentIntentId;

    /** Stripe Charge ID (for reference only). */
    private final String     stripeChargeId;

    /** Stripe Refund ID (null nếu chưa refund). */
    private final String     stripeRefundId;

    /** Mã lỗi Stripe khi thất bại (null nếu thành công). */
    private final String     failureCode;

    /** Mô tả lỗi khi thất bại (null nếu thành công). */
    private final String     failureMessage;

    /** true nếu transaction thành công. */
    private final boolean    succeeded;

    /** Thời điểm tạo transaction. */
    private final Instant    createdAt;

    /** Thời điểm cập nhật gần nhất. */
    private final Instant    updatedAt;
}

