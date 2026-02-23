package com.ticketmaster.event.domain.model;

/**
 * Enum Value Object biểu diễn trạng thái của một ghế ngồi.
 *
 * <p><b>State machine:</b>
 * <pre>
 *   AVAILABLE ──→ LOCKED   (khi user chọn ghế, booking-service tạo distributed lock)
 *   LOCKED    ──→ BOOKED   (khi payment thành công)
 *   LOCKED    ──→ AVAILABLE (khi booking timeout hoặc payment failed)
 *   BOOKED    ──→ AVAILABLE (khi booking bị cancel sau khi đã confirm)
 * </pre>
 *
 * <p>Event-service lắng nghe {@code seat.status.changed} Kafka topic từ booking-service
 * để cập nhật trạng thái ghế trong DB và invalidate Redis cache.
 *
 * <p>Lưu DB dưới dạng String (EnumType.STRING) để tránh thứ tự enum gây lỗi data.
 */
public enum SeatStatus {

    /**
     * Ghế sẵn sàng để đặt.
     * Hiển thị màu xanh trên seat map.
     */
    AVAILABLE,

    /**
     * Ghế đang bị giữ bởi một booking đang xử lý payment.
     * Distributed lock bởi Redisson trong booking-service.
     * Tự động về AVAILABLE sau {@code BOOKING_SEAT_LOCK_TTL_MINUTES} phút.
     * Hiển thị màu vàng trên seat map.
     */
    LOCKED,

    /**
     * Ghế đã được đặt thành công (payment hoàn tất).
     * Hiển thị màu đỏ trên seat map.
     */
    BOOKED
}