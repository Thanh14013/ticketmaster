package com.ticketmaster.user.domain.model;

/**
 * Enum Value Object định nghĩa các vai trò người dùng trong hệ thống.
 *
 * <p>Dùng làm Spring Security authority (prefix {@code ROLE_} theo convention).
 * Lưu xuống DB dưới dạng String (EnumType.STRING) để tránh thứ tự enum phá vỡ data.
 *
 * <p>Quy tắc phân quyền:
 * <ul>
 *   <li>{@code ROLE_USER}  – người dùng thường: xem events, đặt vé, xem booking của mình</li>
 *   <li>{@code ROLE_ADMIN} – quản trị viên: CRUD events/venues, xem tất cả bookings</li>
 * </ul>
 */
public enum UserRole {

    /**
     * Người dùng thường sau khi đăng ký.
     * Có thể: browse events, book tickets, view own profile/bookings.
     */
    ROLE_USER,

    /**
     * Quản trị viên hệ thống.
     * Có thể: tất cả quyền của ROLE_USER + quản lý events/venues/users.
     */
    ROLE_ADMIN
}