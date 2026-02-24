package com.ticketmaster.booking.application.handler;

import com.ticketmaster.booking.application.command.CancelBookingCommand;
import com.ticketmaster.booking.application.kafka.BookingEventProducer;
import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.repository.BookingRepository;
import com.ticketmaster.booking.domain.service.BookingDomainService;
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
 * Handler cho use case huỷ booking.
 *
 * <p>Xử lý cả 2 trường hợp:
 * <ul>
 *   <li><b>User-initiated:</b> user gọi REST API cancel booking của mình</li>
 *   <li><b>System-initiated:</b> Quartz scheduler (expire) hoặc payment.failed Kafka event</li>
 * </ul>
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>Load Booking và validate quyền (bỏ qua khi systemInitiated=true)</li>
 *   <li>Domain state transition: → CANCELLED</li>
 *   <li>Lưu DB</li>
 *   <li>Release Redisson locks cho tất cả ghế</li>
 *   <li>Publish {@code seat.status.changed} (LOCKED/BOOKED → AVAILABLE)</li>
 *   <li>Huỷ Quartz expire job nếu còn</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelBookingHandler {

    private final BookingRepository       bookingRepository;
    private final BookingDomainService    bookingDomainService;
    private final BookingEventProducer    bookingEventProducer;
    private final RedissonSeatLockService seatLockService;
    private final BookingMapper           bookingMapper;
    private final Scheduler               quartzScheduler;

    @Transactional
    public BookingResponse handle(CancelBookingCommand command) {
        log.info("[CANCEL_BOOKING] bookingId={} userId={} systemInitiated={}",
                command.getBookingId(), command.getUserId(), command.isSystemInitiated());

        // 1. Load booking
        Booking booking;
        if (command.isSystemInitiated()) {
            // System cancel: bỏ qua ownership check
            booking = bookingRepository.findById(command.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Booking", "id", command.getBookingId()));
        } else {
            // User cancel: validate ownership
            booking = bookingDomainService.getBookingForUser(
                    command.getBookingId(), command.getUserId());
        }

        // Idempotency: đã CANCELLED/EXPIRED rồi thì bỏ qua
        if (booking.getStatus().name().equals("CANCELLED")
                || booking.getStatus().name().equals("EXPIRED")) {
            log.warn("[CANCEL_BOOKING] Booking {} already {} – skipping",
                    command.getBookingId(), booking.getStatus());
            return bookingMapper.toResponse(booking);
        }

        // 2. Xác định previous status để publish đúng Kafka event
        String previousSeatStatus = booking.isConfirmed() ? "BOOKED" : "LOCKED";

        // 3. Domain state transition → CANCELLED
        Booking cancelled = booking.cancel(command.getReason());

        // 4. Lưu DB
        Booking saved = bookingRepository.save(cancelled);
        log.info("[CANCEL_BOOKING] Booking {} cancelled. Reason: {}", saved.getId(), command.getReason());

        // 5. Release Redisson locks
        saved.getSeatIds().forEach(seatId -> {
            try {
                seatLockService.unlock(seatId);
            } catch (Exception e) {
                log.debug("[CANCEL_BOOKING] Could not unlock seat {}: {}", seatId, e.getMessage());
            }
        });

        // 6. Publish seat.status.changed → AVAILABLE (event-service sẽ cập nhật availableSeats)
        saved.getSeatIds().forEach(seatId ->
            bookingEventProducer.publishSeatStatusChanged(
                    seatId, saved.getEventId(), previousSeatStatus, "AVAILABLE", saved.getId())
        );

        // 7. Huỷ Quartz job nếu còn
        cancelExpireJob(saved.getId());

        return bookingMapper.toResponse(saved);
    }

    private void cancelExpireJob(String bookingId) {
        try {
            quartzScheduler.deleteJob(JobKey.jobKey("expire-" + bookingId, "booking-expire"));
        } catch (SchedulerException e) {
            log.debug("[QUARTZ] Could not delete expire job for booking {}: {}", bookingId, e.getMessage());
        }
    }
}