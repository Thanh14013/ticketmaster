package com.ticketmaster.registry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bảo vệ Eureka dashboard bằng HTTP Basic Authentication.
 *
 * <p>Credentials được đọc từ {@code application.yml}:
 * <pre>
 *   spring.security.user.name:     ${EUREKA_USERNAME}
 *   spring.security.user.password: ${EUREKA_PASSWORD}
 * </pre>
 *
 * <p>Các microservice khi đăng ký vào Eureka phải truyền credentials
 * trong {@code eureka.client.serviceUrl.defaultZone}:
 * <pre>
 *   http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@service-registry:8761/eureka/
 * </pre>
 *
 * <p><b>Tại sao tắt CSRF?</b>
 * Eureka clients gửi POST {@code /eureka/apps/{appId}} để register/heartbeat
 * mà không có CSRF token (REST call, không phải browser form).
 * Bật CSRF sẽ khiến toàn bộ Eureka client registration thất bại.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Tắt CSRF: Eureka REST clients không dùng browser session
            .csrf(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                // Cho phép Docker healthcheck gọi actuator/health mà không cần auth
                .requestMatchers("/actuator/health").permitAll()
                // Tất cả requests còn lại (Eureka dashboard, REST API) cần đăng nhập
                .anyRequest().authenticated()
            )

            // HTTP Basic: dùng được cả từ browser lẫn Eureka client REST
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}