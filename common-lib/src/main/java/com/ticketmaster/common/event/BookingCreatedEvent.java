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
 * Kafka event phát ra bởi {@code booking-service} khi booking mới được tạo.
 *
 * <p><b>Topic:</b> {@code booking.created}
 * <p><b>Producer:</b> booking-service → {@code BookingEventProducer}
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>payment-service   – bắt đầu xử lý thanh toán</li>
 *   <li>notification-service – gửi email "Booking pending payment"</li>
 * </ul>
 *
 * <p><b>Kafka message key:</b> {@code bookingId} (đảm bảo ordering per booking)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedEvent {

    /** UUID của event này – dùng cho idempotency check ở consumer. */
    private String eventId;

    /** ID của booking vừa được tạo. */
    private String bookingId;

    /** ID của user thực hiện booking. */
    private String userId;

    /** Email của user (để notification-service gửi mail không cần query DB). */
    private String userEmail;

    /** Tên event (concert/show) – để hiển thị trong notification. */
    private String eventName;

    /** ID của các ghế được booking. */
    private List<String> seatIds;

    /** Tổng tiền cần thanh toán. */
    private BigDecimal totalAmount;

    /** Đơn vị tiền tệ (vd: "USD", "VND"). */
    @Builder.Default
    private String currency = "USD";

    /** Thời điểm event xảy ra. */
    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Factory Method ───────────────────────────────────────────

    public static BookingCreatedEvent of(String bookingId, String userId, String userEmail,
                                          String eventName, List<String> seatIds,
                                          BigDecimal totalAmount) {
        return BookingCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingId)
                .userId(userId)
                .userEmail(userEmail)
                .eventName(eventName)
                .seatIds(seatIds)
                .totalAmount(totalAmount)
                .currency("USD")
                .occurredAt(Instant.now())
                .build();
    }
}