package com.ticketmaster.gateway.filter;

import com.ticketmaster.common.security.JwtUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication GatewayFilter – validate token trên mọi protected route.
 *
 * <p>Được đăng ký là {@code AbstractGatewayFilterFactory} để dùng được
 * trong cả YAML ({@code - AuthenticationFilter}) lẫn Java DSL
 * ({@code f.filter(authenticationFilter.apply(new Config()))}).
 *
 * <p><b>Flow xử lý:</b>
 * <pre>
 *   Client request
 *     → Kiểm tra header "Authorization: Bearer {token}"
 *     → Extract và validate JWT bằng JwtUtils
 *     → Nếu valid: forward request kèm X-User-Id, X-User-Email, X-User-Role
 *     → Nếu invalid: trả 401 Unauthorized, KHÔNG forward
 * </pre>
 *
 * <p><b>Headers được inject vào downstream request:</b>
 * <ul>
 *   <li>{@code X-User-Id}    – userId từ JWT subject, dùng để gán ownership</li>
 *   <li>{@code X-User-Email} – email, dùng cho notification service</li>
 *   <li>{@code X-User-Role}  – role, dùng cho authorization tại service</li>
 * </ul>
 *
 * <p>Downstream services TIN TƯỞNG các headers này mà không cần validate lại JWT.
 * Đây là mô hình "trust the gateway" – phù hợp khi services chỉ accessible
 * trong internal Docker network (không expose ra ngoài).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final String BEARER_PREFIX  = "Bearer ";
    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLE  = "X-User-Role";

    private final JwtUtils jwtUtils;

    public AuthenticationFilter() {
        super(Config.class);
        this.jwtUtils = null; // Spring sẽ inject qua @RequiredArgsConstructor
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // ── 1. Kiểm tra Authorization header ──────────────────
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("[AUTH] Missing or invalid Authorization header: {} {}",
                        request.getMethod(), request.getPath());
                return sendUnauthorized(exchange, "Missing or invalid Authorization header");
            }

            // ── 2. Extract token ───────────────────────────────────
            String token = authHeader.substring(BEARER_PREFIX.length());

            // ── 3. Validate token ──────────────────────────────────
            try {
                if (!jwtUtils.validateToken(token)) {
                    log.warn("[AUTH] Token validation failed: {} {}", request.getMethod(), request.getPath());
                    return sendUnauthorized(exchange, "Invalid or expired token");
                }

                // ── 4. Extract claims ──────────────────────────────
                String userId = jwtUtils.extractUserId(token);
                String email  = jwtUtils.extractEmail(token);
                String role   = jwtUtils.extractRole(token);

                log.debug("[AUTH] Authenticated user={} role={} → {} {}",
                        userId, role, request.getMethod(), request.getPath());

                // ── 5. Inject user info vào downstream request headers ──
                // Microservices đọc X-User-Id để biết ai đang thực hiện action
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header(HEADER_USER_ID,    userId != null ? userId : "")
                        .header(HEADER_USER_EMAIL, email  != null ? email  : "")
                        .header(HEADER_USER_ROLE,  role   != null ? role   : "")
                        // Xóa Authorization header gốc khỏi downstream request
                        // (microservices không cần và không nên thấy token)
                        .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException e) {
                log.warn("[AUTH] Token expired: {}", e.getMessage());
                return sendUnauthorized(exchange, "Token has expired");

            } catch (MalformedJwtException | SignatureException e) {
                log.warn("[AUTH] Token invalid: {}", e.getMessage());
                return sendUnauthorized(exchange, "Token is invalid");

            } catch (Exception e) {
                log.error("[AUTH] Unexpected error during token validation: {}", e.getMessage(), e);
                return sendInternalError(exchange);
            }
        };
    }

    // ── Response Helpers ─────────────────────────────────────────

    private Mono<Void> sendUnauthorized(ServerWebExchange exchange, String message) {
        return sendJsonError(exchange, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", message);
    }

    private Mono<Void> sendInternalError(ServerWebExchange exchange) {
        return sendJsonError(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred");
    }

    private Mono<Void> sendJsonError(ServerWebExchange exchange,
                                      HttpStatus status,
                                      String errorCode,
                                      String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                """
                {"success":false,"errorCode":"%s","message":"%s","status":%d}
                """,
                errorCode, message, status.value()).trim();

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Config class bắt buộc của {@link AbstractGatewayFilterFactory}.
     * Hiện không có config fields – mọi cấu hình qua Spring beans.
     */
    public static class Config {
        // Có thể thêm: excludePatterns, requiredRoles, v.v.
    }
}