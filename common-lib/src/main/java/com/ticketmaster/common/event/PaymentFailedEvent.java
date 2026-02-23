package com.ticketmaster.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event phát ra bởi {@code payment-service} khi thanh toán thất bại.
 *
 * <p><b>Topic:</b> {@code payment.failed}
 * <p><b>Producer:</b> payment-service → {@code PaymentEventProducer}
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>booking-service      – chuyển booking sang CANCELLED, release ghế</li>
 *   <li>notification-service – gửi email thông báo thanh toán thất bại</li>
 * </ul>
 *
 * <p><b>Kafka message key:</b> {@code bookingId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {

    /** UUID của event này – dùng cho idempotency check ở consumer. */
    private String eventId;

    /** ID booking tương ứng. */
    private String bookingId;

    /** ID user. */
    private String userId;

    /** Email user để gửi thông báo. */
    private String userEmail;

    /** Số tiền cố gắng thanh toán. */
    private BigDecimal attemptedAmount;

    /** Đơn vị tiền tệ. */
    @Builder.Default
    private String currency = "USD";

    /**
     * Mã lỗi từ Stripe (vd: "card_declined", "insufficient_funds", "expired_card").
     * Xem thêm: https://stripe.com/docs/error-codes
     */
    private String failureCode;

    /** Mô tả lỗi human-readable từ Stripe để hiển thị cho user. */
    private String failureMessage;

    /** Thời điểm thất bại. */
    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Factory Method ───────────────────────────────────────────

    public static PaymentFailedEvent of(String bookingId, String userId, String userEmail,
                                         BigDecimal attemptedAmount,
                                         String failureCode, String failureMessage) {
        return PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .userId(userId)
                .userEmail(userEmail)
                .attemptedAmount(attemptedAmount)
                .currency("USD")
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .occurredAt(Instant.now())
                .build();
    }
}