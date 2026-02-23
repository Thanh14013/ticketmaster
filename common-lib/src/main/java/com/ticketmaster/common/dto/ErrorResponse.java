package com.ticketmaster.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Structured error response trả về khi có exception.
 * Được build và trả về bởi {@link com.ticketmaster.common.exception.GlobalExceptionHandler}.
 *
 * <p>Format chuẩn:
 * <pre>
 * {
 *   "status":    422,
 *   "error":     "Validation Failed",
 *   "message":   "Request validation failed",
 *   "errorCode": "VALIDATION_ERROR",
 *   "path":      "/api/v1/bookings",
 *   "fieldErrors": {
 *     "seatId":  "must not be blank",
 *     "eventId": "must not be null"
 *   },
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 *
 * <p>{@code fieldErrors} chỉ xuất hiện khi có lỗi validation ({@code @Valid}).
 * Các field {@code null} sẽ bị bỏ qua trong JSON nhờ {@code @JsonInclude}.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** HTTP status code (vd: 400, 404, 422, 500). */
    private final int status;

    /** HTTP status phrase (vd: "Bad Request", "Not Found"). */
    private final String error;

    /** Mô tả lỗi human-readable. */
    private final String message;

    /**
     * Mã lỗi machine-readable để client xử lý logic.
     * Convention: SCREAMING_SNAKE_CASE, vd: SEAT_NOT_AVAILABLE, USER_ALREADY_EXISTS.
     */
    private final String errorCode;

    /** Request path gây ra lỗi. */
    private final String path;

    /**
     * Field-level validation errors. Key = field name, Value = error message.
     * Chỉ có khi exception là {@link org.springframework.web.bind.MethodArgumentNotValidException}.
     */
    private final Map<String, String> fieldErrors;

    @Builder.Default
    private final Instant timestamp = Instant.now();
}