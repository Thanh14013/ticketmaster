package com.ticketmaster.event.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response DTO cho Seat Map – trả về danh sách ghế được nhóm theo section.
 *
 * <p>Cấu trúc JSON:
 * <pre>
 * {
 *   "eventId": "abc123",
 *   "eventName": "BLACKPINK World Tour",
 *   "totalSeats": 500,
 *   "availableSeats": 237,
 *   "seatsBySection": {
 *     "section-vip-id": [
 *       { "id": "seat-1", "row": "A", "number": "1", "status": "AVAILABLE", ... },
 *       ...
 *     ],
 *     "section-a-id": [ ... ]
 *   }
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatMapResponse {

    private final String eventId;
    private final String eventName;
    private final int    totalSeats;
    private final int    availableSeats;

    /**
     * Key: sectionId, Value: danh sách ghế trong section đó.
     * Frontend dùng để render từng khu vực trên seat map.
     */
    private final Map<String, List<SeatResponse>> seatsBySection;
}