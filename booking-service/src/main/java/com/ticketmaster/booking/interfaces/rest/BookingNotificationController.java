package com.ticketmaster.booking.interfaces.sse;

import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.service.BookingDomainService;
import com.ticketmaster.booking.infrastructure.persistence.mapper.BookingMapper;
import com.ticketmaster.booking.interfaces.dto.BookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE (Server-Sent Events) Endpoint cho real-time booking notification.
 *
 * <p>Client subscribe vào stream để nhận cập nhật trạng thái booking:
 * <ul>
 *   <li>{@code booking:pending}   – booking vừa được tạo</li>
 *   <li>{@code booking:confirmed} – payment thành công</li>
 *   <li>{@code booking:cancelled} – booking bị huỷ</li>
 *   <li>{@code booking:expired}   – quá 2 phút không thanh toán</li>
 *   <li>{@code heartbeat}         – giữ connection alive mỗi 30 giây</li>
 * </ul>
 *
 * <p><b>SSE vs WebSocket:</b> SSE được chọn vì:
 * <ul>
 *   <li>Đơn giản hơn (HTTP/1.1, không cần upgrade)</li>
 *   <li>Auto-reconnect built-in trong browser</li>
 *   <li>Đủ dùng cho one-way push (server → client)</li>
 *   <li>Dễ scale qua load balancer (sticky session hoặc Redis pub/sub)</li>
 * </ul>
 *
 * <p><b>API Gateway config:</b> Endpoint này cần header
 * {@code X-Accel-Buffering: no} để tắt Nginx buffering (đã config trong RouteConfig).
 *
 * <p><b>Timeout:</b> SseEmitter timeout = 5 phút.
 * Client tự reconnect nếu connection bị đứt.
 *
 * <p><b>Storage:</b> {@link #emitters} là ConcurrentHashMap in-memory.
 * Trong production với nhiều replicas, cần Redis pub/sub để broadcast
 * đến đúng instance đang giữ SSE connection của user.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking SSE", description = "Server-Sent Events cho booking notifications real-time")
public class BookingNotificationController {

    /** SSE emitter timeout: 5 phút (sau đó client auto-reconnect). */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    /** Heartbeat interval: 30 giây để giữ connection alive qua proxy/firewall. */
    private static final long HEARTBEAT_INTERVAL_SEC = 30L;

    /** In-memory store: bookingId → SseEmitter. */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final BookingDomainService bookingDomainService;
    private final BookingMapper        bookingMapper;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newScheduledThreadPool(2);

    /**
     * Subscribe SSE stream để nhận notifications cho một booking.
     *
     * <p>Client gọi endpoint này ngay sau khi POST /bookings thành công,
     * rồi giữ connection open để nhận event CONFIRMED/CANCELLED/EXPIRED.
     *
     * @param bookingId ID booking cần theo dõi
     * @param userId    inject bởi API Gateway (kiểm tra ownership)
     * @return SseEmitter stream
     */
    @GetMapping(value = "/{bookingId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe SSE stream cho booking status updates")
    public SseEmitter subscribeBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader("X-User-Id") String userId) {

        log.info("[SSE] New subscription bookingId={} userId={}", bookingId, userId);

        // Validate ownership
        Booking booking = bookingDomainService.getBookingForUser(bookingId, userId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Đăng ký cleanup callback
        emitter.onCompletion(() -> {
            emitters.remove(bookingId);
            log.debug("[SSE] Emitter completed: bookingId={}", bookingId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(bookingId);
            log.debug("[SSE] Emitter timed out: bookingId={}", bookingId);
        });
        emitter.onError(ex -> {
            emitters.remove(bookingId);
            log.debug("[SSE] Emitter error: bookingId={} error={}", bookingId, ex.getMessage());
        });

        emitters.put(bookingId, emitter);

        // Gửi trạng thái hiện tại ngay lập tức (initial event)
        sendBookingStatus(emitter, booking, "booking:initial");

        // Schedule heartbeat
        scheduleHeartbeat(bookingId, emitter);

        return emitter;
    }

    /**
     * Push booking status update đến client đang subscribe.
     * Được gọi bởi handlers (ConfirmBookingHandler, CancelBookingHandler)
     * sau khi trạng thái booking thay đổi.
     *
     * @param bookingId ID booking có trạng thái mới
     * @param booking   Booking aggregate đã cập nhật
     */
    public void pushBookingUpdate(String bookingId, Booking booking) {
        SseEmitter emitter = emitters.get(bookingId);
        if (emitter == null) {
            log.debug("[SSE] No active subscriber for bookingId={}", bookingId);
            return;
        }

        String eventType = "booking:" + booking.getStatus().name().toLowerCase();
        sendBookingStatus(emitter, booking, eventType);

        // Nếu booking ở trạng thái terminal → đóng connection
        if (!booking.isPendingPayment()) {
            emitter.complete();
            emitters.remove(bookingId);
            log.info("[SSE] Connection closed (terminal status) bookingId={} status={}",
                    bookingId, booking.getStatus());
        }
    }

    // ── Private Helpers ───────────────────────────────────────────

    private void sendBookingStatus(SseEmitter emitter, Booking booking, String eventType) {
        try {
            BookingResponse response = bookingMapper.toResponse(booking);
            emitter.send(SseEmitter.event()
                    .id(booking.getId())
                    .name(eventType)
                    .data(response, MediaType.APPLICATION_JSON));

            log.debug("[SSE] Event sent: bookingId={} type={}", booking.getId(), eventType);
        } catch (IOException e) {
            log.warn("[SSE] Failed to send event bookingId={}: {}", booking.getId(), e.getMessage());
            emitters.remove(booking.getId());
        }
    }

    /**
     * Schedule heartbeat mỗi 30 giây để giữ connection qua Nginx/firewall.
     * Nginx mặc định close connection sau 60 giây không có data.
     */
    private void scheduleHeartbeat(String bookingId, SseEmitter emitter) {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!emitters.containsKey(bookingId)) {
                return; // Connection đã đóng, không cần heartbeat
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("ping"));
            } catch (IOException e) {
                emitters.remove(bookingId);
            }
        }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }
}