package com.ticketmaster.event.infrastructure.persistence.repository;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.event.domain.model.Event;
import com.ticketmaster.event.domain.repository.EventRepository;
import com.ticketmaster.event.infrastructure.persistence.entity.EventJpaEntity;
import com.ticketmaster.event.infrastructure.persistence.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter implementation của {@link EventRepository} (Domain interface).
 */
@Repository
@RequiredArgsConstructor
public class EventJpaRepository implements EventRepository {

    private final SpringDataEventRepository springDataRepository;
    private final EventMapper               eventMapper;

    @Override
    public Event save(Event event) {
        return eventMapper.toDomain(springDataRepository.save(eventMapper.toEntity(event)));
    }

    @Override
    public Optional<Event> findById(String id) {
        return springDataRepository.findById(id).map(eventMapper::toDomain);
    }

    @Override
    public Page<Event> search(String keyword, String city, String category,
                              String status, Pageable pageable) {
        return springDataRepository
                .search(keyword, city, category, status, pageable)
                .map(eventMapper::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return springDataRepository.existsById(id);
    }
}

/**
 * Spring Data JPA với JPQL dynamic search.
 * Dùng COALESCE để handle null params → bỏ qua filter đó.
 */
interface SpringDataEventRepository extends JpaRepository<EventJpaEntity, String> {

    @Query("""
        SELECT e FROM EventJpaEntity e
        WHERE (:status   IS NULL OR e.status   = :status)
          AND (:category IS NULL OR e.category = :category)
          AND (:city     IS NULL OR e.venueName LIKE %:city%)
          AND (:keyword  IS NULL OR e.name LIKE %:keyword%
                                 OR e.description LIKE %:keyword%)
        ORDER BY e.startTime ASC
        """)
    Page<EventJpaEntity> search(
            @Param("keyword")  String keyword,
            @Param("city")     String city,
            @Param("category") String category,
            @Param("status")   String status,
            Pageable pageable);
}