package com.ticketmaster.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event phát ra bởi {@code payment-service} sau khi xử lý thanh toán thành công.
 *
 * <p><b>Topic:</b> {@code payment.processed}
 * <p><b>Producer:</b> payment-service → {@code PaymentEventProducer}
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>booking-service      – chuyển booking sang trạng thái CONFIRMED</li>
 *   <li>notification-service – gửi email xác nhận thanh toán</li>
 * </ul>
 *
 * <p><b>Kafka message key:</b> {@code bookingId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {

    /** UUID của event này – dùng cho idempotency check ở consumer. */
    private String eventId;

    /** ID booking tương ứng (dùng để booking-service lookup và confirm). */
    private String bookingId;

    /** ID user thực hiện thanh toán. */
    private String userId;

    /** ID transaction do Stripe cấp (vd: "pi_3OxxxxxxxxxxxYYY"). */
    private String stripeTransactionId;

    /** ID transaction nội bộ trong payment_db. */
    private String transactionId;

    /** Số tiền đã thanh toán thành công. */
    private BigDecimal amount;

    /** Đơn vị tiền tệ (vd: "USD"). */
    @Builder.Default
    private String currency = "USD";

    /** Thời điểm thanh toán hoàn tất. */
    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Factory Method ───────────────────────────────────────────

    public static PaymentProcessedEvent of(String bookingId, String userId,
                                            String stripeTransactionId, String transactionId,
                                            BigDecimal amount) {
        return PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .userId(userId)
                .stripeTransactionId(stripeTransactionId)
                .transactionId(transactionId)
                .amount(amount)
                .currency("USD")
                .occurredAt(Instant.now())
                .build();
    }
}