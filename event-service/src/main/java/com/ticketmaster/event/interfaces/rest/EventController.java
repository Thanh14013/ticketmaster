package com.ticketmaster.event.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.common.dto.PageResponse;
import com.ticketmaster.event.application.command.CreateEventCommand;
import com.ticketmaster.event.application.command.UpdateEventCommand;
import com.ticketmaster.event.application.handler.CreateEventHandler;
import com.ticketmaster.event.application.handler.SearchEventsHandler;
import com.ticketmaster.event.application.handler.UpdateEventHandler;
import com.ticketmaster.event.application.query.SearchEventsQuery;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.infrastructure.cache.EventCacheService;
import com.ticketmaster.event.infrastructure.persistence.mapper.EventMapper;
import com.ticketmaster.event.interfaces.dto.CreateEventRequest;
import com.ticketmaster.event.interfaces.dto.EventResponse;
import com.ticketmaster.event.interfaces.dto.EventSearchRequest;
import com.ticketmaster.event.interfaces.dto.UpdateEventRequest;
import com.ticketmaster.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho Event management.
 *
 * <p>Base path: {@code /api/v1/events}
 *
 * <p><b>Public endpoints</b> (không cần token):
 * <ul>
 *   <li>{@code GET /api/v1/events/search} – tìm kiếm events</li>
 *   <li>{@code GET /api/v1/events/{id}}   – xem chi tiết event</li>
 * </ul>
 *
 * <p><b>Protected endpoints</b> (cần token + ROLE_ADMIN):
 * <ul>
 *   <li>{@code POST /api/v1/events}        – tạo event</li>
 *   <li>{@code PUT  /api/v1/events/{id}}   – cập nhật event</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Quản lý sự kiện và tìm kiếm")
public class EventController {

    private final CreateEventHandler  createEventHandler;
    private final UpdateEventHandler  updateEventHandler;
    private final SearchEventsHandler searchEventsHandler;
    private final EventRepository     eventRepository;
    private final EventCacheService   eventCacheService;
    private final EventMapper         eventMapper;

    // ── Public Endpoints ──────────────────────────────────────────

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm sự kiện với filter và pagination")
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> searchEvents(
            @ModelAttribute EventSearchRequest request) {

        Sort sort = Sort.by("desc".equalsIgnoreCase(request.getSortDir())
                        ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy());

        SearchEventsQuery query = SearchEventsQuery.builder()
                .keyword(request.getKeyword())
                .city(request.getCity())
                .category(request.getCategory())
                .status("PUBLISHED")
                .pageable(PageRequest.of(request.getPage(),
                        Math.min(request.getSize(), 100), sort))
                .build();

        PageResponse<EventResponse> result = searchEventsHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết event theo ID")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable String id) {
        // Try cache first
        EventResponse cached = eventCacheService.getEvent(id);
        if (cached != null) {
            return ResponseEntity.ok(ApiResponse.ok(cached));
        }

        EventResponse response = eventRepository.findById(id)
                .map(eventMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", id));

        eventCacheService.cacheEvent(id, response);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Admin Endpoints ───────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Tạo event mới (Admin only)")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @RequestHeader("X-User-Id") String userId) {

        CreateEventCommand command = CreateEventCommand.builder()
                .name(request.getName())
                .description(request.getDescription())
                .venueId(request.getVenueId())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .build();

        EventResponse response = createEventHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật event (Admin only)")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable String id,
            @Valid @RequestBody UpdateEventRequest request,
            @RequestHeader("X-User-Id") String userId) {

        UpdateEventCommand command = UpdateEventCommand.builder()
                .eventId(id)
                .name(request.getName())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .status(request.getStatus())
                .build();

        EventResponse response = updateEventHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.ok("Event updated successfully", response));
    }
}