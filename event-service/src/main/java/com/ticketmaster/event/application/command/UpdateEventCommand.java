package com.ticketmaster.event.application.command;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Command object cho use case cập nhật event.
 */
@Getter
@Builder
public class UpdateEventCommand {

    private final String  eventId;
    private final String  name;
    private final String  description;
    private final Instant startTime;
    private final Instant endTime;
    private final String  category;
    private final String  imageUrl;
    /** Trạng thái mới (DRAFT, PUBLISHED, CANCELLED). Null = không thay đổi. */
    private final String  status;
}