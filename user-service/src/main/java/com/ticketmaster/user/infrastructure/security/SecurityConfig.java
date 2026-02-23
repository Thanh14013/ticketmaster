package com.ticketmaster.user.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration cho user-service (Spring MVC, không phải WebFlux).
 *
 * <p>User-service là service duy nhất cần xác thực người dùng thực sự
 * (issue JWT). Các service khác chỉ validate token tại API Gateway.
 *
 * <p><b>Strategy:</b> Stateless JWT – không dùng session, không dùng cookies.
 * Request đến user-service từ API Gateway đã được filter bởi
 * {@link com.ticketmaster.gateway.filter.AuthenticationFilter}.
 *
 * <p><b>Public endpoints:</b>
 * <ul>
 *   <li>{@code POST /api/v1/auth/register} – đăng ký tài khoản</li>
 *   <li>{@code POST /api/v1/auth/login}    – đăng nhập lấy token</li>
 *   <li>{@code POST /api/v1/auth/refresh}  – refresh access token</li>
 *   <li>{@code GET  /actuator/health}       – Docker healthcheck</li>
 *   <li>{@code GET  /actuator/prometheus}   – Prometheus scraping</li>
 * </ul>
 *
 * <p><b>Protected endpoints</b> yêu cầu {@code X-User-Id} header
 * (được inject bởi API Gateway sau khi validate JWT).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity               // Bật @PreAuthorize, @RolesAllowed ở controller/service
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: auth endpoints
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh"
                        ).permitAll()
                        // Public: monitoring (Prometheus scraper, Docker healthcheck)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/actuator/info"
                        ).permitAll()
                        // Protected: tất cả endpoints còn lại
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCrypt với strength=12 (mặc định 10).
     * Strength 12 ≈ 300ms/hash – đủ chậm để chống brute force, đủ nhanh cho UX.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}