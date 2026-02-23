package com.ticketmaster.common.event;

import lombok.AllArgsConstructor;
<parameter name="file_text">package com.ticketmaster.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event phát ra khi booking bị hủy (user chủ động hủy hoặc hết timeout).
 *
 * <p><b>Topic:</b> {@code booking.cancelled}
 * <p><b>Producer:</b> booking-service → {@code BookingEventProducer}
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>event-service        – trả ghế về trạng thái AVAILABLE trong DB và cache</li>
 *   <li>notification-service – gửi email thông báo hủy booking</li>
 * </ul>
 *
 * <p><b>Kafka message key:</b> {@code bookingId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {

    /** UUID của event này – dùng cho idempotency check ở consumer. */
    private String eventId;

    /** ID của booking bị hủy. */
    private String bookingId;

    /** ID của user. */
    private String userId;

    /** Email user để gửi cancellation email. */
    private String userEmail;

    /** Tên sự kiện. */
    private String eventName;

    /** Danh sách ghế được giải phóng để bán lại. */
    private List<String> seatIds;

    /**
     * Lý do hủy booking.
     * Các giá trị định sẵn: USER_REQUESTED, PAYMENT_TIMEOUT, PAYMENT_FAILED, ADMIN.
     */
    private String cancellationReason;

    /** Thời điểm hủy. */
    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Factory Methods ──────────────────────────────────────────

    public static BookingCancelledEvent byUser(String bookingId, String userId,
                                                String userEmail, String eventName,
                                                List<String> seatIds) {
        return BookingCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .userId(userId)
                .userEmail(userEmail)
                .eventName(eventName)
                .seatIds(seatIds)
                .cancellationReason("USER_REQUESTED")
                .occurredAt(Instant.now())
                .build();
    }

    public static BookingCancelledEvent byPaymentTimeout(String bookingId, String userId,
                                                          String userEmail, String eventName,
                                                          List<String> seatIds) {
        return BookingCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .userId(userId)
                .userEmail(userEmail)
                .eventName(eventName)
                .seatIds(seatIds)
                .cancellationReason("PAYMENT_TIMEOUT")
                .occurredAt(Instant.now())
                .build();
    }
}