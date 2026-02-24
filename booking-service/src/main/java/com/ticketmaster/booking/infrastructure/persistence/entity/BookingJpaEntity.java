package com.ticketmaster.booking.infrastructure.persistence.entity;

import com.ticketmaster.booking.domain.model.BookingStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * JPA Entity cho bảng {@code bookings}.
 *
 * <p>Index design:
 * <ul>
 *   <li>(user_id, created_at DESC) – lấy booking history của user</li>
 *   <li>(status, expires_at) – Quartz query expired bookings</li>
 *   <li>(user_id, event_id) – kiểm tra duplicate booking</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_user_id",          columnList = "user_id"),
    @Index(name = "idx_bookings_status_expires",    columnList = "status, expires_at"),
    @Index(name = "idx_bookings_user_event",        columnList = "user_id, event_id"),
    @Index(name = "idx_bookings_created_at",        columnList = "created_at")
})
public class BookingJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Snapshot email để gửi notification không cần join user-service. */
    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** Snapshot tên event để hiển thị trong booking history. */
    @Column(name = "event_name", nullable = false, length = 255)
    private String eventName;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    /** Transaction ID từ Stripe (null khi chưa payment). */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /** Thời điểm booking hết hạn = createdAt + seatLockTtlMinutes. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** CascadeType.ALL để save/delete items cùng lúc với booking. */
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL,
               fetch = FetchType.EAGER, orphanRemoval = true)
    private List<BookingItemJpaEntity> items;
}