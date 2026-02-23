package com.ticketmaster.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Generic response wrapper dùng cho tất cả REST endpoints trong hệ thống.
 *
 * <p>Chuẩn response format:
 * <pre>
 * {
 *   "success": true,
 *   "message": "...",
 *   "data":    { ... },
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 *
 * <p>Dùng các factory method thay vì constructor:
 * <pre>
 * return ResponseEntity.ok(ApiResponse.ok(userDto));
 * return ResponseEntity.created(uri).body(ApiResponse.created(bookingDto));
 * return ResponseEntity.badRequest().body(ApiResponse.error("Invalid input", "INVALID_INPUT"));
 * </pre>
 *
 * @param <T> kiểu dữ liệu của field {@code data}
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String  message;
    private final T       data;
    private final String  errorCode;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ── Factory Methods ──────────────────────────────────────────

    /**
     * 200 OK với data, không có message.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 200 OK với message và data.
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 200 OK với chỉ message, không có data.
     * Dùng cho các thao tác không trả về entity (vd: đổi mật khẩu).
     */
    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * 201 Created sau khi tạo resource mới thành công.
     */
    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Resource created successfully")
                .data(data)
                .build();
    }

    /**
     * Error response với message và errorCode.
     * errorCode dùng để client phân biệt loại lỗi mà không parse message.
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }
}