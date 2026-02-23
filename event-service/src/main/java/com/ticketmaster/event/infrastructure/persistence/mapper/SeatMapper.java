package com.ticketmaster.event.infrastructure.persistence.mapper;

import com.ticketmaster.event.domain.model.Seat;
import com.ticketmaster.event.infrastructure.persistence.entity.SeatJpaEntity;
import com.ticketmaster.event.interfaces.dto.SeatResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper cho {@link Seat} domain model.
 */
@Mapper(componentModel = "spring")
public interface SeatMapper {

    SeatJpaEntity toEntity(Seat seat);

    Seat toDomain(SeatJpaEntity entity);

    @Mapping(target = "displayLabel", expression = "java(seat.getDisplayLabel())")
    @Mapping(target = "available",    expression = "java(seat.isAvailable())")
    SeatResponse toResponse(Seat seat);
}