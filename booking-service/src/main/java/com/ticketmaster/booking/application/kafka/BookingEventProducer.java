package com.ticketmaster.booking.application.kafka;

import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingItem;
import com.ticketmaster.common.event.BookingCreatedEvent;
import com.ticketmaster.common.event.SeatStatusChangedEvent;
import com.ticketmaster.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer cho booking-service.
 *
 * <p><b>Topics được publish:</b>
 * <ul>
 *   <li>{@code booking.created}      – khi booking được tạo mới (PENDING_PAYMENT)</li>
 *   <li>{@code seat.status.changed}  – khi trạng thái ghế thay đổi (lock/book/release)</li>
 * </ul>
 *
 * <p><b>Durability:</b> Producer cấu hình {@code acks=all} + {@code enable.idempotence=true}
 * để đảm bảo mọi booking event đều được ghi xuống Kafka ít nhất 1 lần.
 *
 * <p><b>Kafka message key:</b>
 * <ul>
 *   <li>{@code booking.created}:     key = bookingId (ordering per booking)</li>
 *   <li>{@code seat.status.changed}: key = seatId    (ordering per seat – tránh race condition)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private static final String TOPIC_BOOKING_CREATED     = "booking.created";
    private static final String TOPIC_SEAT_STATUS_CHANGED = "seat.status.changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish {@code booking.created} event sau khi booking được tạo thành công.
     * Consumers: payment-service, notification-service.
     *
     * @param booking Booking aggregate vừa được lưu DB
     */
    public void publishBookingCreated(Booking booking) {
        BookingCreatedEvent event = BookingCreatedEvent.builder()
                .eventId(IdGenerator.newId())
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .userEmail(booking.getUserEmail())
                .eventShowId(booking.getEventId())
                .eventName(booking.getEventName())
                .seatIds(booking.getSeatIds())
                .seatItems(booking.getItems().stream()
                        .map(item -> BookingCreatedEvent.SeatItem.builder()
                                .seatId(item.getSeatId())
                                .sectionId(item.getSectionId())
                                .seatRow(item.getSeatRow())
                                .seatNumber(item.getSeatNumber())
                                .sectionName(item.getSectionName())
                                .price(item.getPrice())
                                .build())
                        .toList())
                .totalAmount(booking.getTotalAmount())
                .expiresAt(booking.getExpiresAt())
                .occurredAt(Instant.now())
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_BOOKING_CREATED, booking.getId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA] Failed to publish booking.created for bookingId={}: {}",
                        booking.getId(), ex.getMessage(), ex);
                // TODO: Lưu vào Outbox table để retry
            } else {
                log.info("[KAFKA] booking.created published | bookingId={} partition={} offset={}",
                        booking.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish {@code seat.status.changed} event khi trạng thái ghế thay đổi.
     * Consumer: event-service (cập nhật DB + invalidate cache).
     *
     * @param seatId         ID ghế thay đổi
     * @param eventShowId    ID event (concert/show)
     * @param previousStatus trạng thái cũ: AVAILABLE | LOCKED | BOOKED
     * @param newStatus      trạng thái mới: AVAILABLE | LOCKED | BOOKED
     * @param bookingId      ID booking liên quan (để tracing)
     */
    public void publishSeatStatusChanged(String seatId, String eventShowId,
                                          String previousStatus, String newStatus,
                                          String bookingId) {
        SeatStatusChangedEvent event = SeatStatusChangedEvent.builder()
                .eventId(IdGenerator.newId())
                .seatId(seatId)
                .eventShowId(eventShowId)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .bookingId(bookingId)
                .occurredAt(Instant.now())
                .build();

        // Key = seatId để đảm bảo ordering per seat (tránh race condition)
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_SEAT_STATUS_CHANGED, seatId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA] Failed to publish seat.status.changed seatId={} {} → {}: {}",
                        seatId, previousStatus, newStatus, ex.getMessage(), ex);
            } else {
                log.debug("[KAFKA] seat.status.changed published | seatId={} {} → {} partition={} offset={}",
                        seatId, previousStatus, newStatus,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}