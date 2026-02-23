package com.ticketmaster.event.application.handler;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.event.application.command.UpdateEventCommand;
import com.ticketmaster.event.domain.model.Event;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.infrastructure.cache.EventCacheService;
import com.ticketmaster.event.infrastructure.persistence.mapper.EventMapper;
import com.ticketmaster.event.interfaces.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case cập nhật event (info + status transition).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateEventHandler {

    private final EventRepository   eventRepository;
    private final EventCacheService eventCacheService;
    private final EventMapper       eventMapper;

    @Transactional
    public EventResponse handle(UpdateEventCommand command) {
        log.info("[UPDATE_EVENT] Updating eventId={} status={}", command.getEventId(), command.getStatus());

        Event event = eventRepository.findById(command.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", command.getEventId()));

        // Xử lý status transition nếu có
        Event updated = event;
        if ("PUBLISHED".equals(command.getStatus())) {
            updated = event.publish();
        } else if ("CANCELLED".equals(command.getStatus())) {
            updated = event.cancel();
        }

        // Cập nhật các fields thông tin (nếu được cung cấp)
        if (command.getName() != null) {
            updated = Event.builder()
                    .id(updated.getId()).name(command.getName())
                    .description(command.getDescription() != null ? command.getDescription() : updated.getDescription())
                    .venueId(updated.getVenueId()).venueName(updated.getVenueName())
                    .startTime(command.getStartTime() != null ? command.getStartTime() : updated.getStartTime())
                    .endTime(command.getEndTime() != null ? command.getEndTime() : updated.getEndTime())
                    .status(updated.getStatus()).category(command.getCategory() != null ? command.getCategory() : updated.getCategory())
                    .imageUrl(command.getImageUrl() != null ? command.getImageUrl() : updated.getImageUrl())
                    .totalSeats(updated.getTotalSeats()).availableSeats(updated.getAvailableSeats())
                    .createdAt(updated.getCreatedAt()).updatedAt(java.time.Instant.now())
                    .build();
        }

        Event saved = eventRepository.save(updated);

        // Invalidate cache
        eventCacheService.evictEventCache(saved.getId());

        log.info("[UPDATE_EVENT] Event updated id={} status={}", saved.getId(), saved.getStatus());
        return eventMapper.toResponse(saved);
    }
}