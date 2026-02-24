package com.ticketmaster.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Payment Service – Entry point.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Stripe payment processing via Hexagonal Architecture (Ports & Adapters)</li>
 *   <li>Kafka consumer: {@code booking.created} → charge Stripe</li>
 *   <li>Kafka producer: {@code payment.processed} | {@code payment.failed}</li>
 *   <li>REST API: query transaction history, manual refund</li>
 *   <li>Resilience4j Circuit Breaker + Retry for Stripe calls</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "com.ticketmaster")
@EnableDiscoveryClient
@EnableRetry
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

