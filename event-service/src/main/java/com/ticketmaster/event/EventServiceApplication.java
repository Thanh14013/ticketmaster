package com.ticketmaster.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Event Service – Bounded Context: Event Management.
 *
 * <p>Trách nhiệm:
 * <ul>
 *   <li>Quản lý Events và Venues (CRUD, publish, cancel)</li>
 *   <li>Tìm kiếm events với filter/pagination</li>
 *   <li>Seat Map real-time với Redis cache (TTL 5s)</li>
 *   <li>Consume {@code seat.status.changed} từ booking-service để sync trạng thái ghế</li>
 * </ul>
 *
 * <p>Port: {@code 8082} | DB: {@code event_db} | Cache: Redis prefix {@code event:}
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class EventServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}