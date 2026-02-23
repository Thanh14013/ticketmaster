package com.ticketmaster.event.infrastructure.persistence.entity;

import com.ticketmaster.event.domain.model.SeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * JPA Entity cho bảng {@code seats}.
 *
 * <p>Index compound (event_id, status) tối ưu cho 2 query phổ biến nhất:
 * <ul>
 *   <li>Render seat map: {@code WHERE event_id = ?}</li>
 *   <li>Count available: {@code WHERE event_id = ? AND status = 'AVAILABLE'}</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_seats_event_id",        columnList = "event_id"),
        @Index(name = "idx_seats_event_status",    columnList = "event_id, status"),
        @Index(name = "idx_seats_event_section",   columnList = "event_id, section_id")
})
public class SeatJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "section_id", nullable = false, length = 36)
    private String sectionId;

    @Column(name = "row", nullable = false, length = 5)
    private String row;

    @Column(name = "number", nullable = false, length = 5)
    private String number;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status;
}