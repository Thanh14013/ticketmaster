package com.ticketmaster.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive Security configuration cho Spring Cloud Gateway (WebFlux).
 *
 * <p><b>Quan trọng:</b> Gateway chạy trên WebFlux (non-blocking), phải dùng
 * {@code @EnableWebFluxSecurity} và {@link ServerHttpSecurity} –
 * <em>không phải</em> {@code HttpSecurity} của Spring MVC.
 *
 * <p><b>Kiến trúc phân tách trách nhiệm:</b>
 * <ul>
 *   <li>Tầng này: disable CSRF/session, cấu hình stateless</li>
 *   <li>JWT validation: do {@link filter.AuthenticationFilter} đảm nhận
 *       (chạy như GatewayFilter, trước khi forward request)</li>
 *   <li>Authorization (RBAC): do từng microservice tự xử lý</li>
 * </ul>
 *
 * <p>Tất cả routes {@code permitAll()} ở đây vì:
 * <ul>
 *   <li>Public routes ({@code /api/v1/auth/**}) không cần token</li>
 *   <li>Protected routes đã được chặn bởi {@link filter.AuthenticationFilter}
 *       trước khi đến đây – không cần check hai lần</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // Stateless REST API – không cần CSRF protection
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // Không có login page hay HTTP Basic
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

            // JWT là stateless – không có server-side session
            .logout(ServerHttpSecurity.LogoutSpec::disable)

            // JWT validation do AuthenticationFilter (GatewayFilter) xử lý
            .authorizeExchange(exchange -> exchange
                .anyExchange().permitAll()
            )

            .build();
    }
}