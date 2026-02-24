package com.ticketmaster.payment.domain.model;

/**
 * Enum Value Object biểu diễn trạng thái của một Transaction thanh toán.
 *
 * <p><b>State Machine:</b>
 * <pre>
 *   PENDING ──→ PROCESSING ──→ SUCCEEDED
 *                    │
 *                    └──→ FAILED
 *                    └──→ CANCELLED (booking expired trước khi payment xử lý xong)
 *
 *   SUCCEEDED ──→ REFUNDED (khi booking bị cancel sau khi đã confirm)
 * </pre>
 *
 * <p>Lưu DB dưới dạng String (EnumType.STRING).
 */
public enum TransactionStatus {

    /**
     * Transaction vừa được tạo, chưa gửi đến Stripe.
     * Trạng thái khởi tạo khi nhận {@code booking.created} từ Kafka.
     */
    PENDING,

    /**
     * Đang xử lý – đã gửi request đến Stripe, chờ response.
     * Dùng để detect stuck transactions (circuit breaker open).
     */
    PROCESSING,

    /**
     * Thanh toán thành công – Stripe xác nhận charge.
     * Kafka publish {@code payment.processed} → booking-service CONFIRM.
     */
    SUCCEEDED,

    /**
     * Thanh toán thất bại (card declined, insufficient funds, v.v.).
     * Kafka publish {@code payment.failed} → booking-service CANCEL.
     */
    FAILED,

    /**
     * Transaction bị cancel trước khi xử lý xong.
     * Ví dụ: booking expired trong lúc Stripe đang xử lý.
     */
    CANCELLED,

    /**
     * Đã hoàn tiền thành công qua Stripe Refund API.
     * Trạng thái cuối – từ SUCCEEDED sau refund flow.
     */
    REFUNDED
}