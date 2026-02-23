package com.ticketmaster.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global logging filter – ghi log mọi request/response đi qua Gateway.
 *
 * <p>Implement {@link GlobalFilter} để áp dụng cho TẤT CẢ routes,
 * khác với {@link AuthenticationFilter} (chỉ áp dụng cho route được chỉ định).
 *
 * <p>{@code @Order(Ordered.HIGHEST_PRECEDENCE + 1)} – chạy gần đầu nhất
 * trong filter chain, sau chỉ có các built-in filters của Spring Cloud Gateway.
 * Điều này đảm bảo mọi request đều được log, kể cả những request bị
 * từ chối bởi AuthenticationFilter.
 *
 * <p><b>Thông tin được log:</b>
 * <ul>
 *   <li>Request: method, URI, IP, X-User-Id (nếu có)</li>
 *   <li>Response: HTTP status, thời gian xử lý (ms)</li>
 *   <li>Correlation ID: UUID gán cho mỗi request, forward sang downstream
 *       qua header {@code X-Correlation-Id} để trace end-to-end</li>
 * </ul>
 *
 * <p><b>Log format:</b>
 * <pre>
 *   [REQ ] [abc12345] GET /api/v1/events from 192.168.1.1 | user=user-uuid
 *   [RESP] [abc12345] GET /api/v1/events → 200 in 45ms
 * </pre>
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String START_TIME_ATTR       = "requestStartTime";

    @Override
    public int getOrder() {
        // Chạy gần đầu nhất – chỉ sau built-in Spring Cloud Gateway filters
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // ── Sinh Correlation ID ────────────────────────────────────
        // Mỗi request có một ID duy nhất để trace qua toàn bộ microservices
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        final String finalCorrelationId = correlationId;

        // ── Ghi nhận thời điểm bắt đầu ────────────────────────────
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        // ── Log REQUEST ────────────────────────────────────────────
        String userId  = request.getHeaders().getFirst("X-User-Id");
        String clientIp = getClientIp(request);

        log.info("[REQ ] [{}] {} {} from {} | user={}",
                finalCorrelationId,
                request.getMethod(),
                request.getURI().getPath(),
                clientIp,
                userId != null ? userId : "anonymous");

        // ── Inject Correlation ID vào request downstream ───────────
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // ── Log RESPONSE sau khi downstream trả về ─────────────────
        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(() -> {
                    ServerHttpResponse response = mutatedExchange.getResponse();
                    Long startTime = mutatedExchange.getAttribute(START_TIME_ATTR);
                    long duration  = startTime != null
                            ? System.currentTimeMillis() - startTime
                            : -1;

                    int statusCode = response.getStatusCode() != null
                            ? response.getStatusCode().value()
                            : 0;

                    // Log level theo status: 5xx → ERROR, 4xx → WARN, 2xx/3xx → INFO
                    if (statusCode >= 500) {
                        log.error("[RESP] [{}] {} {} → {} in {}ms",
                                finalCorrelationId, request.getMethod(),
                                request.getURI().getPath(), statusCode, duration);
                    } else if (statusCode >= 400) {
                        log.warn("[RESP] [{}] {} {} → {} in {}ms",
                                finalCorrelationId, request.getMethod(),
                                request.getURI().getPath(), statusCode, duration);
                    } else {
                        log.info("[RESP] [{}] {} {} → {} in {}ms",
                                finalCorrelationId, request.getMethod(),
                                request.getURI().getPath(), statusCode, duration);
                    }
                }));
    }

    // ── Private Helpers ──────────────────────────────────────────

    /**
     * Lấy IP thực của client, xử lý trường hợp đứng sau load balancer/nginx.
     * Ưu tiên: X-Forwarded-For > X-Real-IP > remoteAddress.
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For có thể chứa nhiều IP: "client, proxy1, proxy2"
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}