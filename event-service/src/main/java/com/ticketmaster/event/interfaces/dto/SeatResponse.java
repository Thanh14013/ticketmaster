package com.ticketmaster.event.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response DTO cho một ghế ngồi trên seat map.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatResponse {

    private final String     id;
    private final String     sectionId;
    private final String     row;
    private final String     number;
    private final BigDecimal price;
    /** Trạng thái: AVAILABLE | LOCKED | BOOKED */
    private final String     status;
    /** Label hiển thị trên UI: "A-5" */
    private final String     displayLabel;
    /** Shortcut để frontend tô màu xanh/vàng/đỏ */
    private final boolean    available;
}