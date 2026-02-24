package com.ticketmaster.booking.application.handler;

import com.ticketmaster.booking.application.command.ConfirmBookingCommand;
import com.ticketmaster.booking.application.kafka.BookingEventProducer;
import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.repository.BookingRepository;
import com.ticketmaster.booking.infrastructure.lock.RedissonSeatLockService;
import com.ticketmaster.booking.infrastructure.persistence.mapper.BookingMapper;
import com.ticketmaster.booking.interfaces.dto.BookingResponse;
import com.ticketmaster.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case xác nhận booking sau khi payment thành công.
 *
 * <p>Được gọi từ {@link com.ticketmaster.booking.application.kafka.PaymentEventConsumer}
 * khi nhận {@code PaymentProcessedEvent} từ Kafka topic {@code payment.processed}.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>Load Booking theo ID</li>
 *   <li>Gọi {@code booking.confirm(transactionId)} – domain state transition</li>
 *   <li>Lưu Booking đã CONFIRMED vào DB</li>
 *   <li>Publish {@code seat.status.changed} (LOCKED → BOOKED) cho event-service</li>
 *   <li>Huỷ Quartz expire job (không cần expire nữa)</li>
 *   <li>Release Redisson locks (thanh toán xong → không cần giữ lock nữa)</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> Nếu booking đã CONFIRMED (Kafka retry), bỏ qua không throw.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmBookingHandler {

    private final BookingRepository        bookingRepository;
    private final BookingEventProducer     bookingEventProducer;
    private final RedissonSeatLockService  seatLockService;
    private final BookingMapper            bookingMapper;
    private final Scheduler                quartzScheduler;

    @Transactional
    public BookingResponse handle(ConfirmBookingCommand command) {
        log.info("[CONFIRM_BOOKING] bookingId={} transactionId={} kafkaEventId={}",
                command.getBookingId(), command.getTransactionId(), command.getKafkaEventId());

        Booking booking = bookingRepository.findById(command.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking", "id", command.getBookingId()));

        // Idempotency: đã CONFIRMED rồi thì bỏ qua (Kafka retry)
        if (booking.isConfirmed()) {
            log.warn("[CONFIRM_BOOKING] Booking {} already CONFIRMED – skipping (idempotent)",
                    command.getBookingId());
            return bookingMapper.toResponse(booking);
        }

        // 1. Domain state transition: PENDING_PAYMENT → CONFIRMED
        Booking confirmed = booking.confirm(command.getTransactionId());

        // 2. Lưu DB
        Booking saved = bookingRepository.save(confirmed);
        log.info("[CONFIRM_BOOKING] Booking {} confirmed successfully", saved.getId());

        // 3. Publish seat.status.changed: LOCKED → BOOKED cho mỗi ghế
        saved.getSeatIds().forEach(seatId ->
            bookingEventProducer.publishSeatStatusChanged(
                    seatId, saved.getEventId(), "LOCKED", "BOOKED", saved.getId())
        );

        // 4. Huỷ Quartz expire job (booking đã confirmed → không cần expire)
        cancelExpireJob(saved.getId());

        // 5. Release Redisson locks (chuyển sang BOOKED, không cần distributed lock nữa)
        saved.getSeatIds().forEach(seatId -> {
            try {
                seatLockService.unlock(seatId);
            } catch (Exception e) {
                // Lock đã TTL expire → không sao
                log.debug("[CONFIRM_BOOKING] Could not unlock seat {} (already expired): {}",
                        seatId, e.getMessage());
            }
        });

        return bookingMapper.toResponse(saved);
    }

    private void cancelExpireJob(String bookingId) {
        try {
            JobKey jobKey = JobKey.jobKey("expire-" + bookingId, "booking-expire");
            boolean deleted = quartzScheduler.deleteJob(jobKey);
            log.debug("[QUARTZ] Expire job for booking {} deleted={}", bookingId, deleted);
        } catch (SchedulerException e) {
            // Non-critical – log và tiếp tục
            log.warn("[QUARTZ] Could not delete expire job for booking {}: {}",
                    bookingId, e.getMessage());
        }
    }
}