package com.ticketmaster.notification.domain.model;

/**
 * Enum Value Object biểu diễn loại notification.
 *
 * <p>Mỗi type tương ứng với một Kafka event source và một email template:
 * <ul>
 *   <li>{@code BOOKING_CREATED}    → booking.created     → email "Booking pending payment"</li>
 *   <li>{@code PAYMENT_PROCESSED}  → payment.processed   → email "booking-confirmed.html"</li>
 *   <li>{@code PAYMENT_FAILED}     → payment.failed      → email "payment-failed.html"</li>
 *   <li>{@code BOOKING_CANCELLED}  → booking cancelled   → email "Booking cancelled"</li>
 *   <li>{@code BOOKING_EXPIRED}    → booking expired     → email "Seats released"</li>
 * </ul>
 *
 * <p>Lưu DB dưới dạng String (EnumType.STRING).
 */
public enum NotificationType {

    /** Booking vừa được tạo – nhắc user hoàn tất thanh toán trong 2 phút. */
    BOOKING_CREATED,

    /** Thanh toán thành công – xác nhận booking, gửi e-ticket. */
    PAYMENT_PROCESSED,

    /** Thanh toán thất bại – thông báo lý do và hướng dẫn thử lại. */
    PAYMENT_FAILED,

    /** Booking bị huỷ bởi user. */
    BOOKING_CANCELLED,

    /** Booking hết hạn do không thanh toán trong 2 phút. */
    BOOKING_EXPIRED,

    /** Thông báo hệ thống chung (maintenance, announcement). */
    SYSTEM_ANNOUNCEMENT
}