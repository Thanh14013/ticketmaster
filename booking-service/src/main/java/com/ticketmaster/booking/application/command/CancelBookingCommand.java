package com.ticketmaster.booking.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * Command object cho use case huỷ booking.
 * Có thể được tạo bởi user (qua REST) hoặc bởi system (Quartz scheduler / payment.failed).
 */
@Getter
@Builder
public class CancelBookingCommand {

    private final String bookingId;

    /** ID user – null nếu được cancel bởi system (scheduler, payment failed). */
    private final String userId;

    private final String reason;

    /** true nếu được gọi bởi system (scheduler/payment failed), bỏ qua ownership check. */
    @Builder.Default
    private final boolean systemInitiated = false;
}