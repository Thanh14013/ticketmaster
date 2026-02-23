package com.ticketmaster.common.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;

/**
 * Utility class để sinh ID cho các entities và events trong hệ thống.
 *
 * <p>Chiến lược ID:
 * <ul>
 *   <li>UUID v4 – primary keys cho tất cả entities (không dùng auto-increment
 *       vì microservices tạo ID độc lập, không qua một DB trung tâm)</li>
 *   <li>Prefixed ID – human-readable IDs cho booking, transaction
 *       (vd: {@code BK-A1B2C3D4}, {@code TXN-X9Y8Z7W6})</li>
 * </ul>
 *
 * <p>Tại sao dùng UUID thay vì Long auto-increment:
 * <ul>
 *   <li>Không phụ thuộc DB sequence → service có thể tạo ID offline</li>
 *   <li>Không lộ business volume (attacker không biết đã có bao nhiêu user)</li>
 *   <li>Dễ merge data từ nhiều DB instance khi scale</li>
 * </ul>
 */
@UtilityClass
public class IdGenerator {

    /** Prefix cho Booking ID. */
    private static final String BOOKING_PREFIX     = "BK";

    /** Prefix cho Transaction ID. */
    private static final String TRANSACTION_PREFIX = "TXN";

    /** Prefix cho Notification ID. */
    private static final String NOTIFICATION_PREFIX = "NTF";

    // ── UUID Generation ──────────────────────────────────────────

    /**
     * Sinh UUID v4 dạng string không có dấu gạch ngang.
     * Dùng cho primary keys của JPA entities.
     *
     * @return UUID string 32 ký tự, vd: {@code "550e8400e29b41d4a716446655440000"}
     */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Sinh UUID v4 chuẩn có dấu gạch ngang.
     * Dùng cho Kafka event IDs.
     *
     * @return UUID string 36 ký tự, vd: {@code "550e8400-e29b-41d4-a716-446655440000"}
     */
    public static String newUuid() {
        return UUID.randomUUID().toString();
    }

    // ── Prefixed IDs ─────────────────────────────────────────────

    /**
     * Sinh Booking ID có prefix.
     * Format: {@code "BK-XXXXXXXX"} (8 ký tự hex viết hoa).
     *
     * @return vd: {@code "BK-A1B2C3D4"}
     */
    public static String newBookingId() {
        return BOOKING_PREFIX + "-" + shortHex();
    }

    /**
     * Sinh Transaction ID có prefix.
     * Format: {@code "TXN-XXXXXXXX"}.
     *
     * @return vd: {@code "TXN-X9Y8Z7W6"}
     */
    public static String newTransactionId() {
        return TRANSACTION_PREFIX + "-" + shortHex();
    }

    /**
     * Sinh Notification ID có prefix.
     * Format: {@code "NTF-XXXXXXXX"}.
     *
     * @return vd: {@code "NTF-P3Q4R5S6"}
     */
    public static String newNotificationId() {
        return NOTIFICATION_PREFIX + "-" + shortHex();
    }

    // ── Validation ───────────────────────────────────────────────

    /**
     * Kiểm tra một string có phải UUID hợp lệ không.
     *
     * @param id string cần kiểm tra
     * @return {@code true} nếu là UUID hợp lệ
     */
    public static boolean isValidUuid(String id) {
        if (id == null || id.isBlank()) return false;
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ── Private Helpers ──────────────────────────────────────────

    /**
     * Lấy 8 ký tự hex đầu của UUID mới (đủ ngẫu nhiên cho display purposes).
     * Xác suất collision thấp, chỉ dùng cho human-readable IDs không phải PK.
     */
    private static String shortHex() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
}