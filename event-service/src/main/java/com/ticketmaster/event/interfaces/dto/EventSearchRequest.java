package com.ticketmaster.event.interfaces.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Query parameters cho {@code GET /api/v1/events/search}.
 * Dùng {@code @Setter} để Spring MVC bind query params.
 */
@Getter
@Setter
@NoArgsConstructor
public class EventSearchRequest {

    /** Từ khóa tìm kiếm (optional). */
    private String keyword;

    /** Lọc theo thành phố (optional). */
    private String city;

    /** Lọc theo thể loại: CONCERT, SPORT, THEATER, FESTIVAL (optional). */
    private String category;

    /** Page number, bắt đầu từ 0 (default: 0). */
    private int page = 0;

    /** Page size (default: 20, max: 100). */
    private int size = 20;

    /** Sort field (default: startTime). */
    private String sortBy = "startTime";

    /** Sort direction: asc hoặc desc (default: asc). */
    private String sortDir = "asc";
}