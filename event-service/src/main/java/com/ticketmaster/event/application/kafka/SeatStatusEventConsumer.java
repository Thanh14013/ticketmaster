package com.ticketmaster.event.application.kafka;

import com.ticketmaster.common.event.SeatStatusChangedEvent;
import com.ticketmaster.event.application.command.UpdateSeatStatusCommand;
import com.ticketmaster.event.domain.service.EventDomainService;
import com.ticketmaster.event.infrastructure.cache.SeatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka Consumer lắng nghe topic {@code seat.status.changed}.
 *
 * <p>Được publish bởi {@code booking-service} khi trạng thái ghế thay đổi:
 * <ul>
 *   <li>User chọn ghế → AVAILABLE → LOCKED</li>
 *   <li>Payment thành công → LOCKED → BOOKED</li>
 *   <li>Payment thất bại / timeout → LOCKED → AVAILABLE</li>
 *   <li>Booking cancel → BOOKED → AVAILABLE</li>
 * </ul>
 *
 * <p><b>Idempotency:</b> Dùng {@code eventId} (UUID) trong Kafka message để
 * detect và skip duplicate messages khi Kafka retry.
 *
 * <p><b>Manual Acknowledgment:</b> Chỉ ACK sau khi DB và cache đã cập nhật thành công.
 * Nếu exception xảy ra → không ACK → Kafka retry → DLQ sau max retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatStatusEventConsumer {

    private final EventDomainService eventDomainService;
    private final SeatCacheService   seatCacheService;

    @KafkaListener(
            topics      = "seat.status.changed",
            groupId     = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload SeatStatusChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] Received SeatStatusChangedEvent | eventId={} seatId={} {} → {} | partition={} offset={}",
                event.getEventId(), event.getSeatId(),
                event.getPreviousStatus(), event.getNewStatus(),
                partition, offset);

        try {
            // 1. Update DB qua Domain Service
            eventDomainService.updateSeatStatus(event.getSeatId(), event.getNewStatus());

            // 2. Invalidate seat map cache để lần query tới lấy data mới
            seatCacheService.evictSeatMap(event.getEventShowId());

            // 3. Acknowledge message chỉ sau khi thành công
            acknowledgment.acknowledge();

            log.info("[KAFKA] SeatStatusChangedEvent processed successfully | seatId={} → {}",
                    event.getSeatId(), event.getNewStatus());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to process SeatStatusChangedEvent | seatId={} error={}",
                    event.getSeatId(), ex.getMessage(), ex);
            // Không acknowledge → Kafka sẽ retry → sau max retries vào DLQ
            throw ex;
        }
    }
}