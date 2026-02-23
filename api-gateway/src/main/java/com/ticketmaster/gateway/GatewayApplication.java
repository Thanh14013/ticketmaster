package com.ticketmaster.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway – Entry point duy nhất của toàn hệ thống Ticketmaster.
 *
 * <p>Xây dựng trên Spring Cloud Gateway (WebFlux/Reactor): non-blocking, reactive.
 *
 * <p><b>Trách nhiệm:</b>
 * <ul>
 *   <li><b>Routing</b>       – forward request đến đúng microservice qua Eureka {@code lb://}</li>
 *   <li><b>Authentication</b>  – validate JWT trên mọi protected route
 *                               ({@link filter.AuthenticationFilter})</li>
 *   <li><b>Rate Limiting</b>   – giới hạn request/phút per IP qua Redis Token Bucket</li>
 *   <li><b>Circuit Breaker</b> – fallback tự động khi service down
 *                               ({@link fallback.FallbackController})</li>
 *   <li><b>CORS</b>            – whitelist frontend origins ({@link config.CorsConfig})</li>
 *   <li><b>Logging</b>         – request/response audit log ({@link filter.LoggingFilter})</li>
 * </ul>
 *
 * <p>Routes cấu hình tại {@code application.yml} + {@link config.RouteConfig}.
 * <p>Port: {@code 8080}
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}