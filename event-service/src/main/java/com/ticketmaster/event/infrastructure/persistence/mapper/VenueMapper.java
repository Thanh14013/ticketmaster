package com.ticketmaster.event.infrastructure.persistence.mapper;

import com.ticketmaster.event.domain.model.SeatSection;
import com.ticketmaster.event.domain.model.Venue;
import com.ticketmaster.event.infrastructure.persistence.entity.SeatSectionJpaEntity;
import com.ticketmaster.event.infrastructure.persistence.entity.VenueJpaEntity;
import com.ticketmaster.event.interfaces.dto.VenueResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper cho {@link Venue} aggregate và {@link SeatSection} entity.
 */
@Mapper(componentModel = "spring")
public interface VenueMapper {

    @Mapping(target = "sections", ignore = true)   // Sections mapped via cascade, avoid loop
    VenueJpaEntity toEntity(Venue venue);

    Venue toDomain(VenueJpaEntity entity);

    VenueResponse toResponse(Venue venue);

    SeatSectionJpaEntity toSectionEntity(SeatSection section);

    SeatSection toSectionDomain(SeatSectionJpaEntity entity);
}