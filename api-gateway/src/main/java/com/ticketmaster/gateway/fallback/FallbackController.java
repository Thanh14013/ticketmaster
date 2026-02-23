package com.ticketmaster.gateway.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller xử lý circuit breaker open state.
 *
 * <p>Khi một microservice down hoặc response time vượt quá threshold,
 * Resilience4j circuit breaker mở ra và forward request đến đây
 * thay vì tiếp tục gọi service đang có vấn đề.
 *
 * <p>Cấu hình circuit breaker trong {@code application.yml}:
 * <pre>
 *   filters:
 *     - name: CircuitBreaker
 *       args:
 *         name: user-service
 *         fallbackUri: forward:/fallback/user-service
 * </pre>
 *
 * <p><b>Response format nhất quán với {@code ApiResponse}:</b>
 * <pre>
 * {
 *   "success":   false,
 *   "errorCode": "SERVICE_UNAVAILABLE",
 *   "message":   "user-service is temporarily unavailable...",
 *   "service":   "user-service",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 *
 * <p>HTTP 503 Service Unavailable cho phép client phân biệt
 * "service down" (503) với "business error" (4xx) hay "server bug" (500).
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // ── Per-service fallback endpoints ───────────────────────────

    @GetMapping("/user-service")
    public Mono<ResponseEntity<Map<String, Object>>> userServiceFallback(
            ServerWebExchange exchange) {
        return fallback("user-service", "Authentication and user management", exchange);
    }

    @GetMapping("/event-service")
    public Mono<ResponseEntity<Map<String, Object>>> eventServiceFallback(
            ServerWebExchange exchange) {
        return fallback("event-service", "Event browsing and seat selection", exchange);
    }

    @GetMapping("/booking-service")
    public Mono<ResponseEntity<Map<String, Object>>> bookingServiceFallback(
            ServerWebExchange exchange) {
        return fallback("booking-service", "Ticket booking", exchange);
    }

    @GetMapping("/payment-service")
    public Mono<ResponseEntity<Map<String, Object>>> paymentServiceFallback(
            ServerWebExchange exchange) {
        return fallback("payment-service", "Payment processing", exchange);
    }

    @GetMapping("/notification-service")
    public Mono<ResponseEntity<Map<String, Object>>> notificationServiceFallback(
            ServerWebExchange exchange) {
        return fallback("notification-service", "Notifications", exchange);
    }

    // ── Catch-all fallback ────────────────────────────────────────

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> genericFallback(
            ServerWebExchange exchange) {
        return fallback("unknown-service", "The requested service", exchange);
    }

    // ── Private Helper ────────────────────────────────────────────

    private Mono<ResponseEntity<Map<String, Object>>> fallback(
            String serviceName, String serviceDescription,
            ServerWebExchange exchange) {

        // Lấy correlation ID từ request để trace trong log
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Correlation-Id");

        log.warn("[FALLBACK] [{}] Circuit breaker open for service: {}",
                correlationId != null ? correlationId : "?", serviceName);

        Map<String, Object> body = Map.of(
            "success",   false,
            "errorCode", "SERVICE_UNAVAILABLE",
            "message",   String.format(
                "%s is temporarily unavailable. Please try again in a few moments.",
                serviceDescription),
            "service",   serviceName,
            "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body));
    }
}