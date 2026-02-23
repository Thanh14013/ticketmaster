package com.ticketmaster.event.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO cho Venue.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VenueResponse {

    private final String              id;
    private final String              name;
    private final String              address;
    private final String              city;
    private final String              country;
    private final int                 capacity;
    private final String              displayAddress;
    private final List<SeatSectionResponse> sections;
    private final Instant             createdAt;

    /**
     * Nested DTO cho SeatSection – tránh tạo file riêng cho class đơn giản.
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SeatSectionResponse {
        private final String     id;
        private final String     name;
        private final String     description;
        private final java.math.BigDecimal basePrice;
        private final int        totalSeats;
    }
}