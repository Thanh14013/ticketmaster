package com.ticketmaster.payment.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * Programmatic Resilience4j configuration cho Stripe gateway.
 *
 * <p>Bổ sung thêm event listeners để logging/monitoring các state transitions.
 * Các thông số chính được khai báo trong {@code application.yml} (resilience4j section).
 *
 * <p><b>CircuitBreaker:</b>
 * <ul>
 *   <li>CLOSED  → mọi request qua Stripe</li>
 *   <li>OPEN    → fail-fast 30s, publish {@code payment.failed} ngay lập tức</li>
 *   <li>HALF_OPEN → thử 3 request, nếu OK thì CLOSED lại</li>
 * </ul>
 *
 * <p><b>Retry:</b> 3 lần, 2s wait, chỉ retry IOException/SocketTimeout
 * (không retry card_declined vì đó là expected failure).
 */
@Slf4j
@Configuration
public class Resilience4jConfig {


    /**
     * CircuitBreaker registry với event logging.
     * Khai báo Bean này để đăng ký event listeners – config values từ application.yml.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        // Đăng ký event listeners để log state transitions
        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    String cbName = event.getAddedEntry().getName();
                    event.getAddedEntry().getEventPublisher()
                            .onStateTransition(e ->
                                    log.warn("[CIRCUIT_BREAKER] {} state: {} → {}",
                                            cbName,
                                            e.getStateTransition().getFromState(),
                                            e.getStateTransition().getToState()))
                            .onCallNotPermitted(e ->
                                    log.warn("[CIRCUIT_BREAKER] {} OPEN – call not permitted", cbName))
                            .onError(e ->
                                    log.error("[CIRCUIT_BREAKER] {} error recorded: {}",
                                            cbName, e.getThrowable().getMessage()));
                });

        return registry;
    }

    /**
     * Retry registry với Stripe-specific configuration.
     * Override mặc định: chỉ retry network/IO errors, không retry business errors.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(IOException.class, SocketTimeoutException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);

        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    String retryName = event.getAddedEntry().getName();
                    event.getAddedEntry().getEventPublisher()
                            .onRetry(e ->
                                    log.warn("[RETRY] {} attempt #{} | lastThrowable={}",
                                            retryName, e.getNumberOfRetryAttempts(),
                                            e.getLastThrowable() != null
                                                    ? e.getLastThrowable().getMessage()
                                                    : "none"))
                            .onError(e ->
                                    log.error("[RETRY] {} exhausted all retries | lastThrowable={}",
                                            retryName,
                                            e.getLastThrowable() != null
                                                    ? e.getLastThrowable().getMessage()
                                                    : "unknown"));
                });

        return registry;
    }
}

