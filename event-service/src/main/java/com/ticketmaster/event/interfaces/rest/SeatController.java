package com.ticketmaster.event.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.event.application.handler.GetSeatMapHandler;
import com.ticketmaster.event.application.query.GetSeatMapQuery;
import com.ticketmaster.event.interfaces.dto.SeatMapResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho Seat Map.
 * Base path: {@code /api/v1/seats}
 *
 * <p>Đây là endpoint có traffic CAO NHẤT trong hệ thống khi event hot.
 * Mọi request đều được serve từ Redis cache (TTL 5 giây).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
@Tag(name = "Seats", description = "Seat map và trạng thái ghế ngồi")
public class SeatController {

    private final GetSeatMapHandler getSeatMapHandler;

    /**
     * Lấy seat map của một event – nhóm theo section, kèm trạng thái real-time.
     *
     * @param eventId  ID của event
     * @param noCache  nếu true, bypass cache (admin only)
     * @return SeatMapResponse với ghế được nhóm theo khu vực
     */
    @GetMapping("/event/{eventId}")
    @Operation(summary = "Lấy seat map của event (từ Redis cache, TTL 5s)")
    public ResponseEntity<ApiResponse<SeatMapResponse>> getSeatMap(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "true") boolean useCache) {

        GetSeatMapQuery query = GetSeatMapQuery.builder()
                .eventId(eventId)
                .useCache(useCache)
                .build();

        SeatMapResponse response = getSeatMapHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}