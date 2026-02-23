package com.ticketmaster.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server – Service Discovery cho toàn bộ Ticketmaster microservices.
 *
 * <p>Tất cả microservices đăng ký tên + địa chỉ vào đây khi khởi động.
 * API Gateway resolve {@code lb://service-name} → IP thực qua Eureka,
 * cho phép client-side load balancing và zero-downtime deployment.
 *
 * <p>Dashboard: <a href="http://localhost:8761">http://localhost:8761</a>
 * (Yêu cầu HTTP Basic Auth – credentials cấu hình qua {@code EUREKA_USERNAME/PASSWORD})
 *
 * <p>Thứ tự khởi động bắt buộc:
 * <pre>
 *   1. service-registry  ← phải healthy trước
 *   2. api-gateway
 *   3. user / event / booking / payment / notification  (song song)
 * </pre>
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}