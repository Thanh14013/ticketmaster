package com.ticketmaster.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration cho API Gateway.
 *
 * <p>Gateway là điểm tập trung duy nhất xử lý CORS cho toàn hệ thống.
 * Các microservice phía sau không cần cấu hình CORS riêng.
 *
 * <p>Môi trường:
 * <ul>
 *   <li><b>Dev/local</b>: Allow {@code http://localhost:3000} (React) và
 *       {@code http://localhost:5173} (Vite)</li>
 *   <li><b>Production</b>: Chỉ allow domain thực của frontend</li>
 * </ul>
 *
 * <p>Cấu hình qua biến môi trường {@code CORS_ALLOWED_ORIGINS}:
 * <pre>
 *   CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
 * </pre>
 *
 * <p><b>Lưu ý WebFlux:</b> Phải dùng {@link CorsWebFilter} (reactive),
 * không dùng {@code CorsFilter} của Spring MVC.
 */
@Configuration
public class CorsConfig {

    /**
     * Danh sách origins được phép, đọc từ {@code CORS_ALLOWED_ORIGINS}.
     * Fallback: chỉ localhost:3000 nếu biến môi trường không set.
     */
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsRaw;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // ── Origins ───────────────────────────────────────────────
        List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));
        config.setAllowedOrigins(origins);

        // ── Methods ───────────────────────────────────────────────
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // ── Headers ───────────────────────────────────────────────
        config.setAllowedHeaders(List.of(
                "Authorization",       // JWT token
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // ── Expose Headers ────────────────────────────────────────
        // Headers mà browser JavaScript được phép đọc từ response
        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Disposition"  // Cho file download
        ));

        // ── Credentials ───────────────────────────────────────────
        // true: cho phép browser gửi cookies/Authorization header
        // Bắt buộc true khi frontend dùng Authorization header
        config.setAllowCredentials(true);

        // ── Preflight Cache ───────────────────────────────────────
        // Browser cache preflight OPTIONS request trong 3600 giây (1 giờ)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}