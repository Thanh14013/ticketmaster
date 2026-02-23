package com.ticketmaster.event.application.command;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Command object cho use case tạo event mới.
 */
@Getter
@Builder
public class CreateEventCommand {

    private final String  name;
    private final String  description;
    private final String  venueId;
    private final Instant startTime;
    private final Instant endTime;
    private final String  category;
    private final String  imageUrl;
}