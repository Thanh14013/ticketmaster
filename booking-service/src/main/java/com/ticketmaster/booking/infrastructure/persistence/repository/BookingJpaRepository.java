package com.ticketmaster.booking.infrastructure.persistence.repository;

import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingStatus;
import com.ticketmaster.booking.domain.repository.BookingRepository;
import com.ticketmaster.booking.infrastructure.persistence.entity.BookingJpaEntity;
import com.ticketmaster.booking.infrastructure.persistence.mapper.BookingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter implementation của {@link BookingRepository} (Domain interface).
 *
 * <p>Pattern: Adapter giữa domain và Spring Data JPA.
 * Domain layer gọi {@link BookingRepository} interface – không biết class này tồn tại.
 */
@Repository
@RequiredArgsConstructor
public class BookingJpaRepository implements BookingRepository {

    private final SpringDataBookingRepository springDataRepository;
    private final BookingMapper               bookingMapper;

    @Override
    public Booking save(Booking booking) {
        BookingJpaEntity entity = bookingMapper.toEntity(booking);
        // Set back-reference cho items
        if (entity.getItems() != null) {
            entity.getItems().forEach(item -> item.setBooking(entity));
        }
        return bookingMapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Booking> findById(String id) {
        return springDataRepository.findById(id).map(bookingMapper::toDomain);
    }

    @Override
    public Page<Booking> findByUserId(String userId, Pageable pageable) {
        return springDataRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(bookingMapper::toDomain);
    }

    @Override
    public Optional<Booking> findByUserIdAndEventId(String userId, String eventId) {
        return springDataRepository.findTopByUserIdAndEventIdOrderByCreatedAtDesc(userId, eventId)
                .map(bookingMapper::toDomain);
    }

    @Override
    public List<Booking> findExpiredBookings(BookingStatus status, Instant expiresAt) {
        return springDataRepository.findExpiredBookings(status, expiresAt).stream()
                .map(bookingMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(String id) {
        return springDataRepository.existsById(id);
    }
}

/**
 * Spring Data JPA repository – internal, không expose ra ngoài package.
 */
interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, String> {

    Page<BookingJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<BookingJpaEntity> findTopByUserIdAndEventIdOrderByCreatedAtDesc(
            String userId, String eventId);

    /**
     * Tìm booking PENDING_PAYMENT đã hết hạn.
     * Dùng bởi Quartz scheduler recovery (nếu scheduler bị down).
     */
    @Query("""
        SELECT b FROM BookingJpaEntity b
        WHERE b.status = :status
          AND b.expiresAt < :expiresAt
        ORDER BY b.expiresAt ASC
        """)
    List<BookingJpaEntity> findExpiredBookings(
            @Param("status")    BookingStatus status,
            @Param("expiresAt") Instant expiresAt);
}