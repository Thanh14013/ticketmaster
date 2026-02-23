package com.ticketmaster.event.domain.service;

import com.ticketmaster.common.exception.BusinessException;
import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.event.domain.model.Event;
import com.ticketmaster.event.domain.model.Seat;
import com.ticketmaster.event.domain.model.SeatStatus;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.domain.repository.SeatRepository;
import com.ticketmaster.event.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Domain Service cho Event bounded context.
 *
 * <p>Chứa business logic cần phối hợp nhiều aggregate hoặc cần Repository.
 * Application handlers gọi service này, không gọi repository trực tiếp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDomainService {

    private final EventRepository  eventRepository;
    private final VenueRepository  venueRepository;
    private final SeatRepository   seatRepository;

    // ── Validation ────────────────────────────────────────────────

    /**
     * Validate venue tồn tại trước khi tạo event.
     */
    public void validateVenueExists(String venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResourceNotFoundException("Venue", "id", venueId);
        }
    }

    /**
     * Validate event tồn tại và đang bookable.
     */
    public Event validateEventBookable(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        if (!event.isBookable()) {
            throw new BusinessException(
                    "Event is not available for booking (status: " + event.getStatus() + ")",
                    "EVENT_NOT_BOOKABLE", HttpStatus.CONFLICT);
        }
        return event;
    }

    // ── Seat Operations ───────────────────────────────────────────

    /**
     * Cập nhật trạng thái ghế và sync availableSeats của Event.
     * Được gọi khi nhận {@code SeatStatusChangedEvent} từ Kafka.
     *
     * @param seatId    ID ghế cần cập nhật
     * @param newStatus trạng thái mới
     */
    public void updateSeatStatus(String seatId, String newStatus) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", seatId));

        Seat updated = seat.withStatus(newStatus);
        seatRepository.save(updated);

        // Cập nhật availableSeats trong Event
        long availableCount = seatRepository.countByEventIdAndStatus(
                seat.getEventId(), SeatStatus.AVAILABLE);

        eventRepository.findById(seat.getEventId()).ifPresent(event -> {
            Event updatedEvent = event.updateAvailableSeats((int) availableCount);
            eventRepository.save(updatedEvent);
            log.debug("[DOMAIN] Seat {} → {} | Event {} availableSeats={}",
                    seatId, newStatus, seat.getEventId(), availableCount);
        });
    }

    /**
     * Lấy danh sách ghế để render seat map.
     */
    public List<Seat> getSeatMap(String eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", "id", eventId);
        }
        return seatRepository.findByEventId(eventId);
    }
}