package com.ticketmaster.notification.application.service;

import com.ticketmaster.notification.domain.model.Notification;
import com.ticketmaster.notification.domain.repository.NotificationRepository;
import com.ticketmaster.notification.infrastructure.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Application Service xử lý push SSE notification real-time về client.
 *
 * <p>Client subscribe SSE stream tại
 * {@code GET /api/v1/notifications/stream} (yêu cầu JWT).
 * Service này push notification ngay khi có event Kafka mới.
 *
 * <p><b>SseEmitterRegistry:</b> Quản lý danh sách connection đang active.
 * Mỗi userId có thể có nhiều connection (tab/device khác nhau).
 *
 * <p><b>Payload:</b> JSON object chứa notification info để client hiển thị
 * toast/badge/dropdown notification.
 *
 * <p><b>Async:</b> Push SSE không block Kafka consumer thread.
 *
 * <p><b>Fail-safe:</b> Nếu SSE push thất bại (client đã disconnect) →
 * chỉ log warning, không throw exception.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseNotificationService {

    private final SseEmitterRegistry     sseEmitterRegistry;
    private final NotificationRepository notificationRepository;

    /**
     * Push notification real-time đến tất cả SSE connections của user.
     *
     * @param notification Notification đã lưu DB cần push
     */
    @Async("notificationTaskExecutor")
    public void push(Notification notification) {
        String userId = notification.getUserId();
        log.debug("[SSE] Pushing notification type={} to userId={}", notification.getType(), userId);

        if (!sseEmitterRegistry.hasActiveConnections(userId)) {
            log.debug("[SSE] No active SSE connections for userId={} – skipping push", userId);
            return;
        }

        Map<String, Object> payload = buildPayload(notification);

        sseEmitterRegistry.getEmitters(userId).forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.getId())
                        .name("notification:" + notification.getType().name().toLowerCase())
                        .data(payload, MediaType.APPLICATION_JSON));

                log.debug("[SSE] ✅ Pushed to userId={} type={}",
                        userId, notification.getType());

            } catch (IOException e) {
                // Client đã disconnect → remove emitter
                log.debug("[SSE] Client disconnected, removing emitter for userId={}: {}",
                        userId, e.getMessage());
                sseEmitterRegistry.removeEmitter(userId, emitter);
            }
        });

        // Cập nhật ssePushed = true trong DB
        notificationRepository.save(notification.markSsePushed());
    }

    /**
     * Push thông báo unread count mới cho user.
     * Gọi khi có notification mới để client update badge số.
     *
     * @param userId    ID user
     * @param unreadCount số notification chưa đọc mới nhất
     */
    public void pushUnreadCount(String userId, long unreadCount) {
        if (!sseEmitterRegistry.hasActiveConnections(userId)) return;

        Map<String, Object> payload = Map.of(
                "type",        "unread_count",
                "unreadCount", unreadCount
        );

        sseEmitterRegistry.getEmitters(userId).forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("unread_count")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                sseEmitterRegistry.removeEmitter(userId, emitter);
            }
        });
    }

    // ── Private Helpers ───────────────────────────────────────────

    private Map<String, Object> buildPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id",            notification.getId());
        payload.put("type",          notification.getType().name());
        payload.put("title",         notification.getTitle());
        payload.put("message",       notification.getMessage());
        payload.put("referenceId",   notification.getReferenceId());
        payload.put("referenceType", notification.getReferenceType());
        payload.put("read",          notification.isRead());
        payload.put("createdAt",     notification.getCreatedAt().toString());
        return payload;
    }
}