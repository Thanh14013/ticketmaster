package com.ticketmaster.event.application.query;

import lombok.Builder;
import lombok.Getter;

/**
 * Query object để lấy seat map của một event.
 */
@Getter
@Builder
public class GetSeatMapQuery {

    private final String eventId;

    /**
     * Nếu true: ưu tiên lấy từ Redis cache.
     * Nếu false: bypass cache, lấy trực tiếp từ DB (dùng cho admin).
     */
    @Builder.Default
    private final boolean useCache = true;
}