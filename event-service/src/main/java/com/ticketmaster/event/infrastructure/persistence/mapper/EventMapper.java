package com.ticketmaster.event.infrastructure.persistence.mapper;

import com.ticketmaster.event.domain.model.Event;
import com.ticketmaster.event.infrastructure.persistence.entity.EventJpaEntity;
import com.ticketmaster.event.interfaces.dto.EventResponse;
import org.mapstruct.Mapper;

/**
 * MapStruct Mapper cho {@link Event} domain model.
 *
 * <p>Event domain model và JPA entity có cùng flat structure → mapping 1-1, không cần custom logic.
 */
@Mapper(componentModel = "spring")
public interface EventMapper {

    EventJpaEntity toEntity(Event event);

    Event toDomain(EventJpaEntity entity);

    EventResponse toResponse(Event event);
}