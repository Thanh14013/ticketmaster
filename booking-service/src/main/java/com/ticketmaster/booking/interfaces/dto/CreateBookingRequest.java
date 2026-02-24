package com.ticketmaster.booking.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO cho {@code POST /api/v1/bookings}.
 */
@Getter
@NoArgsConstructor
public class CreateBookingRequest {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotEmpty(message = "At least 1 seat must be selected")
    @Size(max = 8, message = "Maximum 8 seats per booking")
    private List<String> seatIds;
}