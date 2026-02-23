package com.ticketmaster.event.application.handler;

import com.ticketmaster.common.dto.PageResponse;
import com.ticketmaster.event.application.query.SearchEventsQuery;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.infrastructure.persistence.mapper.EventMapper;
import com.ticketmaster.event.interfaces.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case tìm kiếm sự kiện với filter + pagination.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>Nhận {@link SearchEventsQuery} từ application service</li>
 *   <li>Delegate xuống EventRepository với full-text search</li>
 *   <li>Map sang {@link EventResponse} và wrap trong {@link PageResponse}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchEventsHandler {

    private final EventRepository eventRepository;
    private final EventMapper     eventMapper;

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> handle(SearchEventsQuery query) {
        log.debug("[SEARCH_EVENTS] keyword='{}' city='{}' category='{}' status='{}'",
                query.getKeyword(), query.getCity(), query.getCategory(), query.getStatus());

        Page<EventResponse> page = eventRepository
                .search(query.getKeyword(), query.getCity(),
                        query.getCategory(), query.getStatus(),
                        query.getPageable())
                .map(eventMapper::toResponse);

        return PageResponse.from(page);
    }
}