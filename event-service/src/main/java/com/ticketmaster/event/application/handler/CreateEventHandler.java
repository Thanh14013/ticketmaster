package com.ticketmaster.event.application.handler;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.common.util.IdGenerator;
import com.ticketmaster.event.application.command.CreateEventCommand;
import com.ticketmaster.event.domain.model.Event;
import com.ticketmaster.event.domain.model.Venue;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.domain.service.EventDomainService;
import com.ticketmaster.event.domain.repository.VenueRepository;
import com.ticketmaster.event.infrastructure.persistence.mapper.EventMapper;
import com.ticketmaster.event.interfaces.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case tạo event mới.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>Validate venue tồn tại</li>
 *   <li>Tạo Event aggregate với status=DRAFT</li>
 *   <li>Lưu vào DB</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateEventHandler {

    private final EventDomainService eventDomainService;
    private final EventRepository    eventRepository;
    private final VenueRepository    venueRepository;
    private final EventMapper        eventMapper;

    @Transactional
    public EventResponse handle(CreateEventCommand command) {
        log.info("[CREATE_EVENT] Creating event '{}' at venueId={}", command.getName(), command.getVenueId());

        // 1. Validate venue
        eventDomainService.validateVenueExists(command.getVenueId());
        Venue venue = venueRepository.findById(command.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue", "id", command.getVenueId()));

        // 2. Create aggregate
        Event event = Event.create(
                IdGenerator.newId(),
                command.getName(),
                command.getDescription(),
                venue.getId(),
                venue.getName(),
                command.getStartTime(),
                command.getEndTime(),
                command.getCategory(),
                command.getImageUrl(),
                venue.getCapacity()
        );

        // 3. Save
        Event saved = eventRepository.save(event);
        log.info("[CREATE_EVENT] Event created id={}", saved.getId());

        return eventMapper.toResponse(saved);
    }
}