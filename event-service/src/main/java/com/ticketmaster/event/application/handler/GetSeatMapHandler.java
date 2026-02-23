package com.ticketmaster.event.application.handler;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.event.application.query.GetSeatMapQuery;
import com.ticketmaster.event.domain.model.Seat;
import com.ticketmaster.event.domain.model.SeatSection;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.domain.repository.SeatRepository;
import com.ticketmaster.event.infrastructure.cache.SeatCacheService;
import com.ticketmaster.event.infrastructure.persistence.mapper.SeatMapper;
import com.ticketmaster.event.interfaces.dto.SeatMapResponse;
import com.ticketmaster.event.interfaces.dto.SeatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler cho use case lấy seat map của event.
 *
 * <p><b>Cache Strategy:</b>
 * <ul>
 *   <li>Nếu {@code useCache=true}: kiểm tra Redis trước → miss thì query DB và warm cache</li>
 *   <li>Nếu {@code useCache=false}: luôn query DB (dành cho admin real-time view)</li>
 * </ul>
 *
 * <p>Seats được nhóm theo section để frontend render seat map dạng khu vực.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetSeatMapHandler {

    private final EventRepository   eventRepository;
    private final SeatRepository    seatRepository;
    private final SeatCacheService  seatCacheService;
    private final SeatMapper        seatMapper;

    @Transactional(readOnly = true)
    public SeatMapResponse handle(GetSeatMapQuery query) {
        log.debug("[SEAT_MAP] eventId={} useCache={}", query.getEventId(), query.isUseCache());

        // 1. Validate event exists
        var event = eventRepository.findById(query.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", query.getEventId()));

        // 2. Try cache first
        if (query.isUseCache()) {
            SeatMapResponse cached = seatCacheService.getSeatMap(query.getEventId());
            if (cached != null) {
                log.debug("[SEAT_MAP] Cache HIT for eventId={}", query.getEventId());
                return cached;
            }
        }

        // 3. Cache MISS → query DB
        log.debug("[SEAT_MAP] Cache MISS for eventId={} → querying DB", query.getEventId());
        List<Seat> seats = seatRepository.findByEventId(query.getEventId());

        // 4. Group by sectionId
        Map<String, List<SeatResponse>> bySectionId = seats.stream()
                .collect(Collectors.groupingBy(
                        Seat::getSectionId,
                        Collectors.mapping(seatMapper::toResponse, Collectors.toList())
                ));

        SeatMapResponse response = SeatMapResponse.builder()
                .eventId(event.getId())
                .eventName(event.getName())
                .totalSeats(event.getTotalSeats())
                .availableSeats(event.getAvailableSeats())
                .seatsBySection(bySectionId)
                .build();

        // 5. Warm cache (async-safe: Redis SET with TTL)
        if (query.isUseCache()) {
            seatCacheService.cacheSeatMap(query.getEventId(), response);
        }

        return response;
    }
}