package com.ticketmaster.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * User Service – Bounded Context: Identity & Access Management.
 *
 * <p>Trách nhiệm duy nhất: quản lý identity của người dùng.
 * <ul>
 *   <li>Đăng ký tài khoản (register)</li>
 *   <li>Xác thực (login) và phát hành JWT tokens</li>
 *   <li>Quản lý hồ sơ cá nhân (profile)</li>
 * </ul>
 *
 * <p>Service này là nơi DUY NHẤT issue JWT token trong hệ thống.
 * Các service khác chỉ validate token tại API Gateway.
 *
 * <p>Port: {@code 8081}
 * <p>Database: {@code user_db} (PostgreSQL)
 * <p>Cache: Redis, key prefix {@code users::}
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}