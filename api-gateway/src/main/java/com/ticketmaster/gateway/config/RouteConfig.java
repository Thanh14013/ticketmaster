package com.ticketmaster.gateway.config;

import com.ticketmaster.gateway.filter.AuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route configuration bằng Java DSL.
 *
 * <p>Routes cơ bản (auth, rate limiting, circuit breaker) được khai báo
 * trong {@code application.yml} cho dễ đọc. Class này chỉ định nghĩa
 * các route cần logic đặc biệt không thể cấu hình qua YAML:
 * <ul>
 *   <li>Stripe Webhook – bỏ qua JWT auth, preserve raw body</li>
 *   <li>SSE endpoint   – override response timeout, tắt buffering</li>
 * </ul>
 *
 * <p>Service name trong {@code lb://service-name} phải khớp với
 * {@code spring.application.name} đã đăng ký vào Eureka.
 */
@Configuration
@RequiredArgsConstructor
public class RouteConfig {

    private final AuthenticationFilter authenticationFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── Stripe Webhook ──────────────────────────────────────────
            // Stripe gọi trực tiếp, KHÔNG có JWT → bỏ qua AuthenticationFilter.
            // Stripe signature được verify trong payment-service bằng webhook secret.
            // preserveHostHeader() để Stripe verify Host header trong signature.
            .route("payment-webhook", r -> r
                .path("/api/v1/payments/webhook")
                .and()
                .method("POST")
                .filters(f -> f
                    .preserveHostHeader()
                    .addRequestHeader("X-Gateway-Source", "ticketmaster-gateway")
                )
                .uri("lb://payment-service")
            )

            // ── SSE (Server-Sent Events) ────────────────────────────────
            // Cần JWT (AuthenticationFilter áp dụng).
            // Thêm header để nginx và downstream tắt response buffering.
            // Timeout dài (1 giờ) để keep SSE connection sống.
            .route("notification-sse", r -> r
                .path("/api/v1/notifications/stream/**")
                .filters(f -> f
                    .filter(authenticationFilter.apply(new AuthenticationFilter.Config()))
                    .setResponseHeader("X-Accel-Buffering", "no")
                    .setResponseHeader("Cache-Control", "no-cache")
                )
                .uri("lb://booking-service")
            )

            .build();
    }
}