package com.ticketmaster.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event phát ra bởi {@code booking-service} khi trạng thái ghế thay đổi.
 * Event-service consume để cập nhật DB và invalidate Redis cache.
 *
 * <p><b>Topic:</b> {@code seat.status.changed} (6 partitions – high traffic)
 * <p><b>Producer:</b> booking-service → {@code BookingEventProducer}
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>event-service – cập nhật {@code SeatJpaEntity} và {@code SeatCacheService}</li>
 * </ul>
 *
 * <p><b>Kafka message key:</b> {@code seatId} (đảm bảo ordering per seat,
 * tránh race condition khi cùng 1 ghế bị book/release đồng thời)
 *
 * <p><b>SeatStatus lifecycle:</b>
 * <pre>
 *   AVAILABLE → LOCKED (khi user chọn ghế, Redisson lock)
 *   LOCKED    → BOOKED (khi payment thành công)
 *   LOCKED    → AVAILABLE (khi booking timeout hoặc payment failed)
 *   BOOKED    → AVAILABLE (khi booking bị cancel sau refund)
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusChangedEvent {

    /** UUID của event này – dùng cho idempotency check ở consumer. */
    private String eventId;

    /** ID của ghế bị thay đổi trạng thái. */
    private String seatId;

    /** ID của event (concert/show) chứa ghế này. */
    private String eventShowId;

    /**
     * Trạng thái cũ của ghế trước khi thay đổi.
     * Giá trị: AVAILABLE | LOCKED | BOOKED
     */
    private String previousStatus;

    /**
     * Trạng thái mới của ghế sau khi thay đổi.
     * Giá trị: AVAILABLE | LOCKED | BOOKED
     */
    private String newStatus;

    /**
     * ID booking liên quan (null nếu ghế được trả về AVAILABLE).
     */
    private String bookingId;

    /** Thời điểm thay đổi trạng thái. */
    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Factory Methods ──────────────────────────────────────────

    public static SeatStatusChangedEvent locked(String seatId, String eventShowId,
                                                  String bookingId) {
        return SeatStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .seatId(seatId)
                .eventShowId(eventShowId)
                .previousStatus("AVAILABLE")
                .newStatus("LOCKED")
                .bookingId(bookingId)
                .occurredAt(Instant.now())
                .build();
    }

    public static SeatStatusChangedEvent booked(String seatId, String eventShowId,
                                                  String bookingId) {
        return SeatStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .seatId(seatId)
                .eventShowId(eventShowId)
                .previousStatus("LOCKED")
                .newStatus("BOOKED")
                .bookingId(bookingId)
                .occurredAt(Instant.now())
                .build();
    }

    public static SeatStatusChangedEvent released(String seatId, String eventShowId,
                                                    String previousStatus) {
        return SeatStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .seatId(seatId)
                .eventShowId(eventShowId)
                .previousStatus(previousStatus)
                .newStatus("AVAILABLE")
                .bookingId(null)
                .occurredAt(Instant.now())
                .build();
    }
}