package com.ticketmaster.payment.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO cho webhook Stripe và các payment status responses.
 *
 * <p>Dùng cho endpoint xác nhận thanh toán thành công/thất bại từ webhook
 * hoặc polling từ frontend.
 *
 * <p>Khác với {@link TransactionResponse}:
 * <ul>
 *   <li>{@code TransactionResponse} – chi tiết đầy đủ cho internal use</li>
 *   <li>{@code PaymentResponse}     – simplified summary cho client-facing APIs</li>
 * </ul>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    /** Internal transaction ID. */
    private final String     transactionId;

    /** ID booking liên kết. */
    private final String     bookingId;

    /**
     * Kết quả thanh toán.
     * Possible values: PENDING | PROCESSING | SUCCEEDED | FAILED | CANCELLED | REFUNDED
     */
    private final String     status;

    /** Số tiền đã thanh toán. */
    private final BigDecimal amount;

    /** Đơn vị tiền tệ. */
    private final String     currency;

    /**
     * Mô tả phương thức thanh toán.
     * Ví dụ: "Visa •••• 4242 (12/26)"
     */
    private final String     paymentMethodDisplay;

    /**
     * Mã lỗi khi thanh toán thất bại.
     * Null nếu thành công.
     * Ví dụ: "card_declined", "insufficient_funds", "expired_card"
     */
    private final String     failureCode;

    /**
     * Mô tả lỗi human-readable khi thất bại.
     * Null nếu thành công.
     */
    private final String     failureMessage;

    /** true = thanh toán đã được xử lý thành công. */
    private final boolean    succeeded;

    /** Thời điểm xử lý thanh toán. */
    private final Instant    processedAt;

    // ── Factory Methods ──────────────────────────────────────────

    public static PaymentResponse from(TransactionResponse txn) {
        return PaymentResponse.builder()
                .transactionId(txn.getId())
                .bookingId(txn.getBookingId())
                .status(txn.getStatus())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .paymentMethodDisplay(txn.getPaymentMethodDisplay())
                .failureCode(txn.getFailureCode())
                .failureMessage(txn.getFailureMessage())
                .succeeded(txn.isSucceeded())
                .processedAt(txn.getUpdatedAt())
                .build();
    }
}

