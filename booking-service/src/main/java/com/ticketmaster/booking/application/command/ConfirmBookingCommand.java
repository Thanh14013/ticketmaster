package com.ticketmaster.booking.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * Command object cho use case xác nhận booking sau khi payment thành công.
 * Được tạo từ {@code PaymentProcessedEvent} Kafka message trong
 * {@link com.ticketmaster.booking.application.kafka.PaymentEventConsumer}.
 */
@Getter
@Builder
public class ConfirmBookingCommand {

    private final String bookingId;
    private final String transactionId;
    private final String userId;
    /** Kafka event ID để đảm bảo idempotency. */
    private final String kafkaEventId;
}