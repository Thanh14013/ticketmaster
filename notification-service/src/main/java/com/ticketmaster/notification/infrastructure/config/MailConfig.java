package com.ticketmaster.notification.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Mail và Async Executor configuration cho notification-service.
 *
 * <p><b>AsyncTaskExecutor (notificationTaskExecutor):</b>
 * Thread pool riêng cho email gửi async và SSE push.
 * Tách khỏi default Spring async executor để tránh blocking Kafka consumer threads.
 *
 * <p><b>Pool sizing:</b>
 * <ul>
 *   <li>corePoolSize=5: Đủ cho traffic thông thường</li>
 *   <li>maxPoolSize=20: Handle burst traffic (flash sale events)</li>
 *   <li>queueCapacity=100: Buffer notifications khi thread pool busy</li>
 * </ul>
 *
 * <p><b>JavaMailSender:</b> Auto-configured bởi Spring Boot
 * qua {@code spring.mail.*} trong application.yml.
 * Không cần define bean thủ công.
 */
@Configuration
@EnableAsync
public class MailConfig {

    /**
     * Thread pool executor cho email gửi async và SSE push async.
     * Được dùng bởi {@code @Async("notificationTaskExecutor")} annotation.
     */
    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}