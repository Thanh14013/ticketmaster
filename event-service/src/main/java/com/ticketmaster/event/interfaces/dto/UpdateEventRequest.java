package com.ticketmaster.event.interfaces.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO cho {@code PUT /api/v1/events/{id}}.
 * Tất cả fields đều optional (partial update).
 */
@Getter
@NoArgsConstructor
public class UpdateEventRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 5000)
    private String description;

    private Instant startTime;

    private Instant endTime;

    private String category;

    private String imageUrl;

    /** Thay đổi status: PUBLISHED hoặc CANCELLED. */
    @Pattern(regexp = "^(PUBLISHED|CANCELLED)$",
            message = "Status must be PUBLISHED or CANCELLED")
    private String status;
}