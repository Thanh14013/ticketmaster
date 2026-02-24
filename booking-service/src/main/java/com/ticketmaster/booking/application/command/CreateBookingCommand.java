package com.ticketmaster.booking.application.command;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Command object cho use case tạo booking mới.
 *
 * <p>Được tạo từ {@link com.ticketmaster.booking.interfaces.dto.CreateBookingRequest}
 * ở interfaces layer, kết hợp với userId từ {@code X-User-Id} header.
 */
@Getter
@Builder
public class CreateBookingCommand {

    /** ID user (lấy từ X-User-Id header inject bởi API Gateway). */
    private final String       userId;

    /** Email user (lấy từ X-User-Email header inject bởi API Gateway). */
    private final String       userEmail;

    /** ID của Event (concert/show) cần đặt vé. */
    private final String       eventId;

    /** Danh sách seatId cần đặt (tối đa 8 ghế). */
    private final List<String> seatIds;
}