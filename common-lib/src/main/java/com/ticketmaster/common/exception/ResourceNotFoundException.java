package com.ticketmaster.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception ném ra khi không tìm thấy resource theo ID hoặc tiêu chí tìm kiếm.
 * Maps tới HTTP 404 Not Found.
 *
 * <p>Sử dụng:
 * <pre>
 * // Tìm theo ID
 * User user = userRepository.findById(userId)
 *         .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
 * // → "User not found with id: 'abc-123'"
 *
 * // Tìm theo field khác
 * Event event = eventRepository.findBySlug(slug)
 *         .orElseThrow(() -> new ResourceNotFoundException("Event", "slug", slug));
 *
 * // Custom message
 * throw new ResourceNotFoundException("Booking has already been cancelled");
 * </pre>
 */
public class ResourceNotFoundException extends BusinessException {

    /**
     * Constructor chuẩn với resourceName, fieldName, fieldValue.
     * Tạo message: "{resourceName} not found with {fieldName}: '{fieldValue}'"
     *
     * @param resourceName tên resource (vd: "User", "Event", "Booking")
     * @param fieldName    tên field dùng để tìm (vd: "id", "email", "slug")
     * @param fieldValue   giá trị field dùng để tìm
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
            String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue),
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND
        );
    }

    /**
     * Constructor với custom message khi cần mô tả chi tiết hơn.
     *
     * @param message mô tả lỗi cụ thể
     */
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}