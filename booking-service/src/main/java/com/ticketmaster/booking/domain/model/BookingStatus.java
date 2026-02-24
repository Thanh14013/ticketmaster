package com.ticketmaster.booking.domain.model;

/**
 * Enum Value Object biểu diễn trạng thái của một Booking.
 *
 * <p><b>State Machine:</b>
 * <pre>
 *                    ┌─────────────────────────────────┐
 *                    │                                 │
 *   CREATE ──→ PENDING_PAYMENT ──→ CONFIRMED           │
 *                    │                                 │
 *                    ├──→ CANCELLED (by user)          │
 *                    │                                 │
 *                    └──→ EXPIRED (Quartz scheduler)───┘
 *                              after 2 minutes
 *
 *   CONFIRMED ──→ CANCELLED (refund flow)
 * </pre>
 *
 * <p>Chỉ có thể chuyển sang CONFIRMED khi nhận {@code PaymentProcessedEvent} từ Kafka.
 * Chỉ có thể EXPIRED khi Quartz {@code SeatReleaseScheduler} chạy sau 2 phút.
 *
 * <p>Lưu DB dưới dạng String (EnumType.STRING).
 */
public enum BookingStatus {

    /**
     * Booking vừa được tạo, đang chờ thanh toán.
     * Ghế đang bị LOCKED bởi Redisson distributed lock (TTL 2 phút).
     * SSE push trạng thái này về client ngay lập tức.
     */
    PENDING_PAYMENT,

    /**
     * Thanh toán thành công – booking hoàn tất.
     * Ghế chuyển sang BOOKED trong event-service qua Kafka.
     * Gửi email xác nhận qua notification-service.
     */
    CONFIRMED,

    /**
     * Booking bị huỷ bởi user hoặc bởi hệ thống (refund flow).
     * Ghế được RELEASE về AVAILABLE.
     */
    CANCELLED,

    /**
     * Booking hết hạn do không thanh toán trong 2 phút.
     * Được set bởi Quartz {@code SeatReleaseScheduler}.
     * Ghế được RELEASE về AVAILABLE.
     */
    EXPIRED
}