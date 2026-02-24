package com.ticketmaster.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * JPA Entity cho bảng {@code booking_items}.
 * Mỗi row = 1 ghế trong 1 booking.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "booking_items", indexes = {
    @Index(name = "idx_booking_items_booking_id", columnList = "booking_id"),
    @Index(name = "idx_booking_items_seat_id",    columnList = "seat_id")
})
public class BookingItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "booking_id", nullable = false, length = 36)
    private String bookingId;

    /** FK → event-service seats.id. Không phải foreign key thật (cross-service). */
    @Column(name = "seat_id", nullable = false, length = 36)
    private String seatId;

    @Column(name = "section_id", nullable = false, length = 36)
    private String sectionId;

    /** Snapshot thông tin ghế tại thời điểm booking. */
    @Column(name = "seat_row", nullable = false, length = 5)
    private String seatRow;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Column(name = "section_name", nullable = false, length = 100)
    private String sectionName;

    /** Giá tại thời điểm booking – immutable. */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", insertable = false, updatable = false)
    private BookingJpaEntity booking;
}