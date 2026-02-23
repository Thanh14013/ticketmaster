package com.ticketmaster.event.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * Command object để cập nhật trạng thái ghế.
 * Được tạo từ {@code SeatStatusChangedEvent} Kafka message.
 */
@Getter
@Builder
public class UpdateSeatStatusCommand {

    private final String seatId;
    private final String eventId;
    private final String newStatus;
    private final String previousStatus;
    /** Kafka event ID để đảm bảo idempotency. */
    private final String kafkaEventId;
}