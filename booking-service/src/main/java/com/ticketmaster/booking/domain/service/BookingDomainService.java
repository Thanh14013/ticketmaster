package com.ticketmaster.booking.domain.service;

import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingItem;
import com.ticketmaster.booking.domain.model.BookingStatus;
import com.ticketmaster.booking.domain.repository.BookingRepository;
import com.ticketmaster.booking.infrastructure.lock.RedissonSeatLockService;
import com.ticketmaster.common.exception.BusinessException;
import com.ticketmaster.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service – Business rules cốt lõi của Booking bounded context.
 *
 * <p>Chứa logic cần phối hợp nhiều aggregate hoặc cần infrastructure service
 * (Redisson lock). Application handlers gọi service này.
 *
 * <p><b>Critical flows:</b>
 * <ul>
 *   <li>{@link #lockSeats} – lấy Redisson distributed lock cho từng seatId</li>
 *   <li>{@link #releaseSeats} – giải phóng tất cả locks của booking</li>
 *   <li>{@link #validateUserBookingLimit} – user không thể có 2 active booking cho cùng event</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingDomainService {

    private final BookingRepository       bookingRepository;
    private final RedissonSeatLockService seatLockService;

    @Value("${booking.seat-lock-ttl-minutes:2}")
    private int seatLockTtlMinutes;

    @Value("${booking.max-seats-per-booking:8}")
    private int maxSeatsPerBooking;

    // ── Validation ─────────────────────────────────────────────────

    /**
     * Validate danh sách ghế hợp lệ trước khi tạo booking.
     *
     * @param seatIds   danh sách ID ghế cần đặt
     * @throws BusinessException nếu vượt quá giới hạn
     */
    public void validateSeatCount(List<String> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BusinessException(
                    "At least 1 seat must be selected",
                    "NO_SEATS_SELECTED", HttpStatus.BAD_REQUEST);
        }
        if (seatIds.size() > maxSeatsPerBooking) {
            throw new BusinessException(
                    "Maximum " + maxSeatsPerBooking + " seats per booking",
                    "TOO_MANY_SEATS", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Validate user không có active booking cho cùng event.
     * Business rule: 1 user chỉ được có 1 active booking per event.
     *
     * @throws BusinessException nếu đã có active booking
     */
    public void validateUserBookingLimit(String userId, String eventId) {
        bookingRepository.findByUserIdAndEventId(userId, eventId).ifPresent(existing -> {
            if (existing.isActive()) {
                throw new BusinessException(
                        "You already have an active booking for this event",
                        "DUPLICATE_BOOKING", HttpStatus.CONFLICT);
            }
        });
    }

    // ── Seat Lock Operations ───────────────────────────────────────

    /**
     * Lấy Redisson distributed lock cho tất cả seats.
     * Nếu bất kỳ ghế nào đã bị lock (user khác đang giữ) → fail fast và release tất cả.
     *
     * @param seatIds danh sách ID ghế cần lock
     * @throws BusinessException nếu có ghế không thể lock (đã bị giữ)
     */
    public void lockSeats(List<String> seatIds) {
        List<String> lockedSeats = new ArrayList<>();

        try {
            for (String seatId : seatIds) {
                boolean locked = seatLockService.tryLock(seatId, seatLockTtlMinutes);
                if (!locked) {
                    // Ghế này đang bị người khác giữ → rollback tất cả locks đã lấy
                    log.warn("[LOCK] Seat {} is already locked – releasing {} acquired locks",
                            seatId, lockedSeats.size());
                    releaseSeatsById(lockedSeats);
                    throw new BusinessException(
                            "Seat " + seatId + " is no longer available. Please select different seats.",
                            "SEAT_ALREADY_LOCKED", HttpStatus.CONFLICT);
                }
                lockedSeats.add(seatId);
                log.debug("[LOCK] Seat {} locked successfully (TTL={}min)", seatId, seatLockTtlMinutes);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Unexpected error → rollback
            releaseSeatsById(lockedSeats);
            throw new BusinessException(
                    "Failed to acquire seat locks. Please try again.",
                    "LOCK_FAILED", HttpStatus.SERVICE_UNAVAILABLE);
        }

        log.info("[LOCK] Successfully locked {} seats", seatIds.size());
    }

    /**
     * Giải phóng tất cả Redisson locks của các ghế trong booking.
     * Gọi khi booking EXPIRED hoặc CANCELLED.
     *
     * @param booking booking cần release seats
     */
    public void releaseSeats(Booking booking) {
        releaseSeatsById(booking.getSeatIds());
    }

    /**
     * Giải phóng lock theo danh sách seatId.
     * Fail-safe: log warning nếu unlock thất bại, không throw exception.
     */
    private void releaseSeatsById(List<String> seatIds) {
        for (String seatId : seatIds) {
            try {
                seatLockService.unlock(seatId);
                log.debug("[LOCK] Seat {} unlocked", seatId);
            } catch (Exception e) {
                // Lock đã TTL expire → không cần unlock, tiếp tục
                log.warn("[LOCK] Failed to unlock seat {} (may have already expired): {}",
                        seatId, e.getMessage());
            }
        }
    }

    // ── Booking Lookup ─────────────────────────────────────────────

    /**
     * Lấy Booking theo ID và validate quyền truy cập của user.
     *
     * @param bookingId ID booking
     * @param userId    ID user đang request
     * @return Booking nếu tìm thấy và user có quyền
     * @throws ResourceNotFoundException nếu không tìm thấy
     * @throws BusinessException         nếu user không có quyền
     */
    public Booking getBookingForUser(String bookingId, String userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(
                    "You don't have permission to access this booking",
                    "FORBIDDEN", HttpStatus.FORBIDDEN);
        }
        return booking;
    }
}