package com.ticketmaster.booking.infrastructure.persistence.mapper;

import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingItem;
import com.ticketmaster.booking.infrastructure.persistence.entity.BookingItemJpaEntity;
import com.ticketmaster.booking.infrastructure.persistence.entity.BookingJpaEntity;
import com.ticketmaster.booking.interfaces.dto.BookingItemResponse;
import com.ticketmaster.booking.interfaces.dto.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct Mapper cho {@link Booking} aggregate.
 *
 * <p>Xử lý:
 * <ul>
 *   <li>{@link Booking} ↔ {@link BookingJpaEntity} (nested items list)</li>
 *   <li>{@link Booking} → {@link BookingResponse}</li>
 *   <li>{@link BookingItem} ↔ {@link BookingItemJpaEntity}</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface BookingMapper {

    // ── Domain → JPA Entity ───────────────────────────────────────

    @Mapping(target = "items", source = "items")
    BookingJpaEntity toEntity(Booking booking);

    @Mapping(target = "booking", ignore = true)  // Avoid circular ref
    BookingItemJpaEntity toItemEntity(BookingItem item);

    List<BookingItemJpaEntity> toItemEntities(List<BookingItem> items);

    // ── JPA Entity → Domain ───────────────────────────────────────

    @Mapping(target = "items", source = "items")
    Booking toDomain(BookingJpaEntity entity);

    BookingItem toItemDomain(BookingItemJpaEntity entity);

    List<BookingItem> toItemDomains(List<BookingItemJpaEntity> entities);

    // ── Domain → Response DTO ─────────────────────────────────────

    @Mapping(target = "itemCount",  expression = "java(booking.getItems() != null ? booking.getItems().size() : 0)")
    @Mapping(target = "active",     expression = "java(booking.isActive())")
    @Mapping(target = "items",      source = "items")
    BookingResponse toResponse(Booking booking);

    @Mapping(target = "displayLabel", expression = "java(item.getDisplayLabel())")
    BookingItemResponse toItemResponse(BookingItem item);

    List<BookingItemResponse> toItemResponses(List<BookingItem> items);
}