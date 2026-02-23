package com.ticketmaster.event.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một khu vực (section/zone) trong Venue.
 *
 * <p>Ví dụ: "Khu VIP", "Khu A", "Khu B", "Khu Đứng".
 * Mỗi Section thuộc về một {@link Venue} và chứa nhiều {@link Seat}.
 *
 * <p><b>Không phải Aggregate Root</b> – chỉ được truy cập thông qua
 * {@link Venue} hoặc trực tiếp qua ID trong context của Event.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSection {

    /** UUID v4 – primary key. */
    private String id;

    /** ID của Venue chứa section này. */
    private String venueId;

    /** Tên hiển thị (vd: "VIP", "Khu A", "Standing Zone"). */
    private String name;

    /** Mô tả thêm về khu vực (vd: "Gần sân khấu nhất"). */
    private String description;

    /**
     * Giá vé cơ bản cho section này.
     * Giá thực tế có thể khác nhau theo event (nhưng đây là giá gốc của venue).
     */
    private BigDecimal basePrice;

    /** Tổng số ghế trong section (tính toán, không nhất thiết phải lưu DB). */
    private int totalSeats;
}