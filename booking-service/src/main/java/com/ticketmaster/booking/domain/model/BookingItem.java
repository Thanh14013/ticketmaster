package com.ticketmaster.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một ghế cụ thể trong một {@link Booking}.
 *
 * <p>Một Booking có thể chứa nhiều BookingItem (1 item = 1 ghế).
 * Ví dụ: User đặt 3 ghế → 1 Booking + 3 BookingItems.
 *
 * <p><b>Không phải Aggregate Root</b> – chỉ được truy cập thông qua
 * {@link Booking} aggregate.
 *
 * <p>Thông tin ghế (row, number, section) được snapshot tại thời điểm booking
 * để tránh phụ thuộc vào event-service sau khi booking đã hoàn tất.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingItem {

    /** UUID v4 – primary key. */
    private String id;

    /** ID của Booking cha. */
    private String bookingId;

    /** ID của ghế trong event-service. */
    private String seatId;

    /** ID của SeatSection (snapshot). */
    private String sectionId;

    /** Hàng ghế (snapshot từ event-service, vd: "A"). */
    private String seatRow;

    /** Số ghế (snapshot từ event-service, vd: "5"). */
    private String seatNumber;

    /** Tên section (snapshot, vd: "VIP"). */
    private String sectionName;

    /** Giá tại thời điểm booking (immutable). */
    private BigDecimal price;

    // ── Domain Method ─────────────────────────────────────────────

    /**
     * Label hiển thị đầy đủ: "VIP – Hàng A, Ghế 5"
     */
    public String getDisplayLabel() {
        return sectionName + " – Hàng " + seatRow + ", Ghế " + seatNumber;
    }
}