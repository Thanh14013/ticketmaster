package com.ticketmaster.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Booking Service – Bounded Context: Booking (CORE).
 *
 * <p>Service quan trọng nhất trong hệ thống, chứa:
 * <ul>
 *   <li>Seat selection với Redisson distributed lock (TTL 2 phút)</li>
 *   <li>Booking lifecycle: PENDING → CONFIRMED | CANCELLED | EXPIRED</li>
 *   <li>Kafka Producer: booking.created, seat.status.changed</li>
 *   <li>Kafka Consumer: payment.processed, payment.failed</li>
 *   <li>Quartz Scheduler (JDBC): auto-expire sau 2 phút</li>
 *   <li>SSE endpoint: real-time push booking status về client</li>
 * </ul>
 *
 * <p>Port: {@code 8083} | DB: {@code booking_db} | Lock: Redis (Redisson)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}