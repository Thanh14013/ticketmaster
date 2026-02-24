package com.ticketmaster.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification Service – Bounded Context: Notification.
 *
 * <p><b>Trách nhiệm:</b>
 * <ul>
 *   <li>Consume: {@code booking.created}, {@code payment.processed}, {@code payment.failed}</li>
 *   <li>Gửi email HTML qua JavaMailSender + Thymeleaf templates</li>
 *   <li>Push SSE real-time notification về client đang kết nối</li>
 *   <li>Lưu lịch sử notification + trạng thái đã đọc/chưa đọc</li>
 * </ul>
 *
 * <p>Port: {@code 8085} | DB: {@code notification_db} | Email: SMTP (Gmail)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}