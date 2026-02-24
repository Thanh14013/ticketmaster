package com.ticketmaster.booking.domain.repository;

import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain Repository interface cho {@link Booking} aggregate.
 * Implementation tại infrastructure layer.
 */
public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(String id);

    /** Lấy tất cả booking của user, hỗ trợ pagination. */
    Page<Booking> findByUserId(String userId, Pageable pageable);

    /** Lấy booking theo user và event. */
    Optional<Booking> findByUserIdAndEventId(String userId, String eventId);

    /**
     * Tìm các booking PENDING_PAYMENT đã hết hạn.
     * Dùng bởi Quartz scheduler để release seats.
     *
     * @param status   trạng thái cần lọc (PENDING_PAYMENT)
     * @param expiresAt thời điểm cutoff (trước thời điểm này = đã hết hạn)
     */
    List<Booking> findExpiredBookings(BookingStatus status, Instant expiresAt);

    boolean existsById(String id);
}