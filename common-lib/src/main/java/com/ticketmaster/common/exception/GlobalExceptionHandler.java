package com.ticketmaster.common.exception;

import com.ticketmaster.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler cho tất cả REST controllers trong mọi service.
 *
 * <p>Mỗi service import common-lib sẽ tự động có handler này nhờ
 * Spring Boot auto-configuration và component scan.
 *
 * <p>Thứ tự ưu tiên xử lý:
 * <ol>
 *   <li>Validation errors ({@code @Valid}) → 422 Unprocessable Entity</li>
 *   <li>{@link BusinessException} và subclasses → HTTP status từ exception</li>
 *   <li>Security exceptions → 401 / 403</li>
 *   <li>HTTP method/format errors → 400 / 405</li>
 *   <li>Catch-all → 500 Internal Server Error</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 1. Validation Errors ─────────────────────────────────────

    /**
     * Bắt lỗi {@code @Valid} / {@code @Validated} trên request body.
     * Trả về map field → error message để client hiển thị đúng chỗ.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field   = ((FieldError) err).getField();
            String message = err.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("[VALIDATION] {} - fields: {}", request.getRequestURI(), fieldErrors);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Validation Failed")
                .message("Request validation failed. Check 'fieldErrors' for details.")
                .errorCode("VALIDATION_ERROR")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Bắt lỗi {@code @Validated} trên path/query params.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(cv -> {
            String field   = cv.getPropertyPath().toString();
            String message = cv.getMessage();
            fieldErrors.put(field, message);
        });

        log.warn("[CONSTRAINT] {} - violations: {}", request.getRequestURI(), fieldErrors);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Parameter validation failed.")
                .errorCode("CONSTRAINT_VIOLATION")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── 2. Business Exceptions ───────────────────────────────────

    /**
     * Bắt tất cả {@link BusinessException} và subclasses:
     * {@link ResourceNotFoundException}, và các exception domain-specific
     * trong từng service (SeatNotAvailableException, v.v.).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        // Log level tùy theo severity của HTTP status
        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("[BUSINESS] {} {} - code: {}, message: {}",
                    ex.getHttpStatus().value(), request.getRequestURI(),
                    ex.getErrorCode(), ex.getMessage());
        } else {
            log.warn("[BUSINESS] {} {} - code: {}, message: {}",
                    ex.getHttpStatus().value(), request.getRequestURI(),
                    ex.getErrorCode(), ex.getMessage());
        }

        ErrorResponse body = ErrorResponse.builder()
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    // ── 3. Security Exceptions ───────────────────────────────────

    /**
     * 401 Unauthorized – chưa xác thực (token thiếu hoặc invalid).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.warn("[AUTH] 401 {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Authentication required. Please login and provide a valid token.")
                .errorCode("AUTHENTICATION_REQUIRED")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * 403 Forbidden – đã xác thực nhưng không có quyền.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("[AUTH] 403 {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You do not have permission to access this resource.")
                .errorCode("ACCESS_DENIED")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ── 4. HTTP-level Errors ─────────────────────────────────────

    /**
     * 400 – Request body không đọc được (JSON malformed, wrong content-type).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("[HTTP] 400 {} - message not readable: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Request body is missing or malformed. Please send valid JSON.")
                .errorCode("MALFORMED_REQUEST")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 400 – Query/path parameter bị thiếu.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("[HTTP] 400 {} - missing param: {}", request.getRequestURI(), ex.getParameterName());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(String.format("Required parameter '%s' is missing.", ex.getParameterName()))
                .errorCode("MISSING_PARAMETER")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 400 – Kiểu dữ liệu của parameter sai (vd: truyền "abc" vào field Long).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String requiredType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "unknown";

        log.warn("[HTTP] 400 {} - type mismatch: {} expects {}",
                request.getRequestURI(), ex.getName(), requiredType);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(String.format(
                        "Parameter '%s' should be of type '%s'.", ex.getName(), requiredType))
                .errorCode("TYPE_MISMATCH")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 405 – HTTP method không được hỗ trợ (vd: POST thay vì GET).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("[HTTP] 405 {} - method {} not allowed", request.getRequestURI(), ex.getMethod());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .message(String.format(
                        "HTTP method '%s' is not supported for this endpoint.", ex.getMethod()))
                .errorCode("METHOD_NOT_ALLOWED")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    // ── 5. Catch-all ─────────────────────────────────────────────

    /**
     * 500 – Bắt tất cả exception không được xử lý ở trên.
     * Log đầy đủ stack trace để debug, nhưng KHÔNG expose detail ra client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtExceptions(
            Exception ex,
            HttpServletRequest request) {

        log.error("[UNHANDLED] 500 {} - {}: {}",
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later or contact support.")
                .errorCode("INTERNAL_SERVER_ERROR")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}