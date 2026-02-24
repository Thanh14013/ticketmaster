package com.ticketmaster.booking.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO cho Booking aggregate.
 * Không bao giờ expose userId nội bộ hay sensitive payment details.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingResponse {

    private final String                  id;
    private final String                  eventId;
    private final String                  eventName;
    private final List<BookingItemResponse> items;
    private final int                     itemCount;
    private final BigDecimal              totalAmount;
    /** PENDING_PAYMENT | CONFIRMED | CANCELLED | EXPIRED */
    private final String                  status;
    private final String                  transactionId;
    private final String                  cancellationReason;
    private final Instant                 expiresAt;
    /** Số giây còn lại trước khi booking expire (null nếu không PENDING). */
    private final Long                    secondsUntilExpiry;
    private final boolean                 active;
    private final Instant                 createdAt;
    private final Instant                 updatedAt;
}