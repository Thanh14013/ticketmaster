package com.ticketmaster.event.application.query;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Pageable;

/**
 * Query object cho use case tìm kiếm events.
 */
@Getter
@Builder
public class SearchEventsQuery {

    /** Từ khóa tìm kiếm theo name/description. Null = tìm tất cả. */
    private final String   keyword;

    /** Lọc theo thành phố. Null = tất cả. */
    private final String   city;

    /** Lọc theo thể loại (CONCERT, SPORT, THEATER, v.v.). Null = tất cả. */
    private final String   category;

    /**
     * Lọc theo trạng thái. Default "PUBLISHED" cho public API.
     * Admin có thể xem DRAFT và CANCELLED.
     */
    @Builder.Default
    private final String   status = "PUBLISHED";

    private final Pageable pageable;
}