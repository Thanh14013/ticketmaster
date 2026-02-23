package com.ticketmaster.event.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA Entity cho bảng {@code events}.
 *
 * <p>Index trên (status, start_time) để tối ưu query tìm kiếm events đang PUBLISHED
 * và sắp diễn ra (chuẩn bị bán vé).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_status_start",    columnList = "status, start_time"),
        @Index(name = "idx_events_venue_id",        columnList = "venue_id"),
        @Index(name = "idx_events_category_status", columnList = "category, status")
})
public class EventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "venue_id", nullable = false, length = 36)
    private String venueId;

    /** Denormalized để tránh JOIN khi search/list events. */
    @Column(name = "venue_name", nullable = false, length = 255)
    private String venueName;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    /** DRAFT | PUBLISHED | CANCELLED | COMPLETED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    /** Cached counter – cập nhật khi nhận Kafka seat status events. */
    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}