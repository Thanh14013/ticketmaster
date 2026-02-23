package com.ticketmaster.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event phát ra khi booking được xác nhận thành công (sau khi payment hoàn tất).
 *
 * <p><b>Topic:</b> {@code booking.confirmed}
 * <p><b>Producer:</b> booking-service → {@code BookingEventProducer}
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>event-service        – đánh dấu ghế là BOOKED trong DB và cache</li>
 *   <li>notification-service – gửi email xác nhận, SSE notification</li>
 * </ul>
 *
 * <p><b>Kafka message key:</b> {@code bookingId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {

    /** UUID của event này – dùng cho idempotency check ở consumer. */
    private String eventId;

    /** ID của booking đã được xác nhận. */
    private String bookingId;

    /** ID của user. */
    private String userId;

    /** Email user để gửi confirmation email. */
    private String userEmail;

    /** Tên sự kiện để hiển thị trong email. */
    private String eventName;

    /** Danh sách ghế đã được đặt thành công. */
    private List<String> seatIds;

    /** Số tiền đã thanh toán. */
    private BigDecimal paidAmount;

    /** ID transaction từ Stripe. */
    private String transactionId;

    /** Thời điểm booking được xác nhận. */
    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Factory Method ───────────────────────────────────────────

    public static BookingConfirmedEvent of(String bookingId, String userId, String userEmail,
                                            String eventName, List<String> seatIds,
                                            BigDecimal paidAmount, String transactionId) {
        return BookingConfirmedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .userId(userId)
                .userEmail(userEmail)
                .eventName(eventName)
                .seatIds(seatIds)
                .paidAmount(paidAmount)
                .transactionId(transactionId)
                .occurredAt(Instant.now())
                .build();
    }
}