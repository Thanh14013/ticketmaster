package com.ticketmaster.event.domain.repository;

import com.ticketmaster.event.domain.model.Seat;
import com.ticketmaster.event.domain.model.SeatStatus;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository interface cho {@link Seat} entity.
 */
public interface SeatRepository {

    Seat save(Seat seat);

    List<Seat> saveAll(List<Seat> seats);

    Optional<Seat> findById(String id);

    /**
     * Lấy tất cả ghế của một event (dùng để render seat map).
     */
    List<Seat> findByEventId(String eventId);

    /**
     * Lấy ghế theo event và section (dùng cho seat map theo khu vực).
     */
    List<Seat> findByEventIdAndSectionId(String eventId, String sectionId);

    /**
     * Đếm số ghế AVAILABLE của một event (dùng để cập nhật availableSeats).
     */
    long countByEventIdAndStatus(String eventId, SeatStatus status);
}