package com.ticketmaster.event.domain.repository;

import com.ticketmaster.event.domain.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Domain Repository interface cho {@link Event} aggregate.
 * Implementation tại infrastructure layer.
 */
public interface EventRepository {

    Event save(Event event);

    Optional<Event> findById(String id);

    /**
     * Tìm kiếm events theo keyword (name, description), city, category, status.
     * Hỗ trợ pagination.
     */
    Page<Event> search(String keyword, String city, String category,
                       String status, Pageable pageable);

    boolean existsById(String id);
}