package com.ticketmaster.common.util;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Utility class cho date/time operations.
 *
 * <p>Toàn bộ hệ thống dùng UTC làm timezone chuẩn.
 * Chỉ convert sang local timezone khi hiển thị cho user cuối.
 *
 * <p>Các formatter được khai báo là constant để tái sử dụng, tránh tạo mới mỗi lần gọi.
 */
@UtilityClass
public class DateUtils {

    public static final ZoneId UTC = ZoneOffset.UTC;

    /** Format ISO 8601 đầy đủ: {@code "2024-01-15T10:30:00"} */
    public static final DateTimeFormatter ISO_LOCAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Format date: {@code "2024-01-15"} */
    public static final DateTimeFormatter ISO_LOCAL_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Format thân thiện cho email/notification: {@code "January 15, 2024 at 10:30 AM"} */
    public static final DateTimeFormatter DISPLAY_DATETIME =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a");

    // ── Conversion ────────────────────────────────────────────────

    /**
     * Chuyển {@link Instant} (UTC) thành {@link LocalDateTime} theo timezone chỉ định.
     *
     * @param instant  thời điểm UTC
     * @param zoneId   timezone đích (vd: {@code ZoneId.of("Asia/Ho_Chi_Minh")})
     * @return LocalDateTime theo timezone chỉ định
     */
    public static LocalDateTime toLocalDateTime(Instant instant, ZoneId zoneId) {
        if (instant == null) return null;
        return instant.atZone(zoneId).toLocalDateTime();
    }

    /**
     * Chuyển {@link Instant} thành {@link LocalDateTime} UTC.
     */
    public static LocalDateTime toUtcLocalDateTime(Instant instant) {
        return toLocalDateTime(instant, UTC);
    }

    /**
     * Chuyển {@link LocalDateTime} UTC thành {@link Instant}.
     */
    public static Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    // ── Formatting ────────────────────────────────────────────────

    /**
     * Format {@link Instant} thành ISO string để log/debug.
     * Output: {@code "2024-01-15T10:30:00"}
     */
    public static String formatForLog(Instant instant) {
        if (instant == null) return "null";
        return ISO_LOCAL_DATETIME.format(instant.atZone(UTC));
    }

    /**
     * Format {@link Instant} thành chuỗi thân thiện cho email/notification.
     * Output: {@code "January 15, 2024 at 10:30 AM"}
     */
    public static String formatForDisplay(Instant instant) {
        if (instant == null) return "";
        return DISPLAY_DATETIME.format(instant.atZone(UTC));
    }

    // ── Comparison ────────────────────────────────────────────────

    /**
     * Kiểm tra một {@link Instant} có trong quá khứ không.
     */
    public static boolean isInPast(Instant instant) {
        return instant != null && instant.isBefore(Instant.now());
    }

    /**
     * Kiểm tra một {@link Instant} có trong tương lai không.
     */
    public static boolean isInFuture(Instant instant) {
        return instant != null && instant.isAfter(Instant.now());
    }

    /**
     * Lấy thời điểm bắt đầu của một ngày (00:00:00 UTC).
     */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(UTC).toInstant();
    }

    /**
     * Lấy thời điểm kết thúc của một ngày (23:59:59.999999999 UTC).
     */
    public static Instant endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC);
    }
}