package com.ticketmaster.notification.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Registry quản lý tất cả SSE connections đang active.
 *
 * <p><b>Storage:</b> {@code ConcurrentHashMap<userId, List<SseEmitter>>}
 * Mỗi userId có thể có nhiều emitters (nhiều browser tab, nhiều device).
 * List dùng {@code CopyOnWriteArrayList} để safe trong môi trường concurrent.
 *
 * <p><b>Heartbeat:</b> Gửi heartbeat mỗi {@code notification.sse.heartbeat-ms} ms
 * để giữ connection qua Nginx (mặc định 60s idle timeout).
 * Nếu gửi heartbeat thất bại → remove emitter (client đã disconnect).
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Client subscribe → {@link #register} tạo emitter mới</li>
 *   <li>Server push notification → {@link #getEmitters} lấy danh sách</li>
 *   <li>Client disconnect / timeout → {@link #removeEmitter} cleanup</li>
 * </ol>
 *
 * <p><b>Scaling:</b> In-memory registry chỉ hoạt động với single instance.
 * Với multiple replicas, cần Redis pub/sub để broadcast đến đúng instance.
 * (Acceptable trong development/staging với 1 replica.)
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    @Value("${notification.sse.timeout-ms:300000}")
    private long sseTimeoutMs;

    @Value("${notification.sse.heartbeat-ms:15000}")
    private long heartbeatMs;

    /** userId → danh sách SseEmitter của user đó. */
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitterMap
            = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler
            = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    // ── Constructor ───────────────────────────────────────────────

    public SseEmitterRegistry() {
        // Schedule heartbeat sau khi bean được tạo
        // Không dùng @PostConstruct vì heartbeatMs chưa được inject
    }

    /**
     * Đăng ký SSE connection mới cho user.
     *
     * @param userId ID user đang subscribe
     * @return SseEmitter mới với timeout đã cấu hình
     */
    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        // Cleanup callbacks
        emitter.onCompletion(() -> {
            removeEmitter(userId, emitter);
            log.debug("[SSE] Emitter completed | userId={} remaining={}",
                    userId, getConnectionCount(userId));
        });
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            log.debug("[SSE] Emitter timed out | userId={}", userId);
        });
        emitter.onError(ex -> {
            removeEmitter(userId, emitter);
            log.debug("[SSE] Emitter error | userId={}: {}", userId, ex.getMessage());
        });

        // Thêm vào registry
        emitterMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Schedule heartbeat cho emitter này
        scheduleHeartbeat(userId, emitter);

        log.info("[SSE] New connection registered | userId={} totalConnections={}",
                userId, getConnectionCount(userId));

        // Gửi initial event để confirm connection
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE connection established for userId: " + userId));
        } catch (IOException e) {
            log.debug("[SSE] Could not send initial event to userId={}", userId);
        }

        return emitter;
    }

    /**
     * Lấy danh sách tất cả emitters của userId.
     *
     * @param userId ID user
     * @return danh sách SseEmitter (empty list nếu không có connection)
     */
    public List<SseEmitter> getEmitters(String userId) {
        return emitterMap.getOrDefault(userId, new CopyOnWriteArrayList<>());
    }

    /**
     * Kiểm tra user có active SSE connection không.
     */
    public boolean hasActiveConnections(String userId) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    /**
     * Remove một emitter cụ thể của user.
     * Tự động cleanup entry trong map nếu list rỗng.
     */
    public void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emitterMap.remove(userId);
                log.debug("[SSE] All connections closed for userId={}", userId);
            }
        }
    }

    /**
     * Remove tất cả connections của một user (khi user logout).
     */
    public void removeAllEmitters(String userId) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.remove(userId);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
            log.info("[SSE] All {} connections removed for userId={}", emitters.size(), userId);
        }
    }

    /** Đếm tổng số connections đang active của user. */
    public int getConnectionCount(String userId) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);
        return emitters != null ? emitters.size() : 0;
    }

    /** Đếm tổng số users đang có active SSE connection. */
    public int getTotalActiveUsers() {
        return emitterMap.size();
    }

    // ── Private Helpers ───────────────────────────────────────────

    /**
     * Schedule heartbeat để giữ connection qua Nginx/firewall.
     * Nếu heartbeat thất bại (IOException) → emitter đã mất → remove.
     */
    private void scheduleHeartbeat(String userId, SseEmitter emitter) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!hasActiveConnections(userId)
                    || !getEmitters(userId).contains(emitter)) {
                return; // Emitter đã bị remove
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("ping"));
            } catch (IOException e) {
                // Client đã disconnect
                removeEmitter(userId, emitter);
            }
        }, heartbeatMs, heartbeatMs, TimeUnit.MILLISECONDS);
    }
}