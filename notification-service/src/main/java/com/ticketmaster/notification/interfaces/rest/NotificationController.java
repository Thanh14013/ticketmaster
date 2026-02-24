package com.ticketmaster.notification.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.notification.domain.model.Notification;
import com.ticketmaster.notification.domain.repository.NotificationRepository;
import com.ticketmaster.notification.infrastructure.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * REST + SSE Controller cho notification-service.
 *
 * <p>Base path: {@code /api/v1/notifications}
 *
 * <p><b>SSE Endpoint:</b>
 * {@code GET /api/v1/notifications/stream} – Client subscribe để nhận
 * real-time notifications (toast alerts, badge count).
 *
 * <p><b>REST Endpoints:</b>
 * <ul>
 *   <li>{@code GET /api/v1/notifications}        – Lấy notification history</li>
 *   <li>{@code GET /api/v1/notifications/unread}  – Lấy notifications chưa đọc</li>
 *   <li>{@code PUT /api/v1/notifications/{id}/read} – Đánh dấu đã đọc</li>
 *   <li>{@code GET /api/v1/notifications/stats}   – Đếm unread count</li>
 * </ul>
 *
 * <p><b>API Gateway config:</b> SSE endpoint cần header
 * {@code X-Accel-Buffering: no} để tắt Nginx response buffering
 * (đã cấu hình trong api-gateway RouteConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "SSE real-time + notification history")
public class NotificationController {

    private final SseEmitterRegistry     sseEmitterRegistry;
    private final NotificationRepository notificationRepository;

    // ── SSE Subscribe ─────────────────────────────────────────────

    /**
     * Subscribe SSE stream để nhận real-time notifications.
     *
     * <p>Client gọi endpoint này một lần sau khi login và giữ connection.
     * Server sẽ push events khi có booking.created, payment.processed, payment.failed.
     *
     * <p><b>Event types được push:</b>
     * <ul>
     *   <li>{@code notification:booking_created}   – Booking pending payment</li>
     *   <li>{@code notification:payment_processed} – Payment confirmed</li>
     *   <li>{@code notification:payment_failed}    – Payment failed</li>
     *   <li>{@code unread_count}                   – Badge count update</li>
     *   <li>{@code heartbeat}                      – Keep-alive ping</li>
     * </ul>
     *
     * @param userId inject bởi API Gateway từ JWT
     * @return SseEmitter stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe SSE stream cho real-time notifications")
    public SseEmitter subscribeNotifications(
            @RequestHeader("X-User-Id") String userId) {

        log.info("[SSE] New subscription | userId={} totalUsers={}",
                userId, sseEmitterRegistry.getTotalActiveUsers());

        return sseEmitterRegistry.register(userId);
    }

    // ── REST Endpoints ────────────────────────────────────────────

    /**
     * Lấy notification history của user (tất cả, bao gồm đã đọc).
     *
     * @param limit số lượng tối đa (default 50)
     */
    @GetMapping
    @Operation(summary = "Lấy notification history")
    public ResponseEntity<ApiResponse<List<Notification>>> getNotifications(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "50") int limit) {

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    /**
     * Lấy notifications chưa đọc.
     */
    @GetMapping("/unread")
    @Operation(summary = "Lấy notifications chưa đọc")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnreadNotifications(
            @RequestHeader("X-User-Id") String userId) {

        List<Notification> unread = notificationRepository.findUnreadByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(unread));
    }

    /**
     * Đánh dấu một notification là đã đọc.
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu notification đã đọc")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {

        notificationRepository.findById(id).ifPresent(notification -> {
            if (notification.getUserId().equals(userId)) {
                notificationRepository.save(notification.markRead());
            }
        });
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", null));
    }

    /**
     * Lấy thống kê notification của user: unread count.
     */
    @GetMapping("/stats")
    @Operation(summary = "Lấy thống kê notification (unread count)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @RequestHeader("X-User-Id") String userId) {

        long unreadCount = notificationRepository.countUnreadByUserId(userId);
        boolean hasActiveStream = sseEmitterRegistry.hasActiveConnections(userId);

        Map<String, Object> stats = Map.of(
                "unreadCount",      unreadCount,
                "hasActiveStream",  hasActiveStream
        );
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}