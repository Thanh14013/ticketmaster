package com.ticketmaster.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception cho tất cả business logic errors trong hệ thống.
 *
 * <p>Tất cả custom exceptions trong các services phải kế thừa từ class này.
 * {@link GlobalExceptionHandler} sẽ bắt và trả về response đúng HTTP status.
 *
 * <p>Ví dụ tạo exception riêng trong một service:
 * <pre>
 * public class SeatNotAvailableException extends BusinessException {
 *     public SeatNotAvailableException(String seatId) {
 *         super(
 *             "Seat " + seatId + " is not available for booking",
 *             "SEAT_NOT_AVAILABLE",
 *             HttpStatus.CONFLICT
 *         );
 *     }
 * }
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Mã lỗi machine-readable để client xử lý logic.
     * Convention: SCREAMING_SNAKE_CASE, vd: SEAT_NOT_AVAILABLE.
     */
    private final String errorCode;

    /** HTTP status sẽ được trả về trong response. */
    private final HttpStatus httpStatus;

    /**
     * Constructor đầy đủ.
     *
     * @param message    mô tả lỗi human-readable
     * @param errorCode  mã lỗi machine-readable
     * @param httpStatus HTTP status code tương ứng
     */
    public BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * Constructor shorthand với default status 400 Bad Request.
     * Dùng cho các input validation errors ở business layer.
     *
     * @param message   mô tả lỗi
     * @param errorCode mã lỗi
     */
    public BusinessException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }

    /**
     * Constructor với cause (dùng khi wrap exception khác).
     *
     * @param message    mô tả lỗi
     * @param errorCode  mã lỗi
     * @param httpStatus HTTP status
     * @param cause      exception gốc
     */
    public BusinessException(String message, String errorCode,
                              HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }
}