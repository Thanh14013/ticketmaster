package com.ticketmaster.event.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Response DTO cho Event.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponse {

    private final String  id;
    private final String  name;
    private final String  description;
    private final String  venueId;
    private final String  venueName;
    private final Instant startTime;
    private final Instant endTime;
    private final String  status;
    private final String  imageUrl;
    private final String  category;
    private final int     totalSeats;
    private final int     availableSeats;
    private final boolean bookable;
    private final Instant createdAt;
    private final Instant updatedAt;
}