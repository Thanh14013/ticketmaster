package com.ticketmaster.booking.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response DTO cho một BookingItem (ghế trong booking).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingItemResponse {

    private final String     id;
    private final String     seatId;
    private final String     sectionId;
    private final String     seatRow;
    private final String     seatNumber;
    private final String     sectionName;
    private final BigDecimal price;
    /** Vd: "VIP – Hàng A, Ghế 5" */
    private final String     displayLabel;
}