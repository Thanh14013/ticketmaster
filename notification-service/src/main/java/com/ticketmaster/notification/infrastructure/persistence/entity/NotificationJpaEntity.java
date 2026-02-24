package com.ticketmaster.notification.infrastructure.persistence.entity;

import com.ticketmaster.notification.domain.model.NotificationType;
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

import java.time.Instant;

/**
 * JPA Entity cho bảng {@code notifications}.
 *
 * <p>Index design:
 * <ul>
 *   <li>{@code (user_id, created_at DESC)} – lấy notification history của user</li>
 *   <li>{@code (user_id, read)} – đếm/lấy notifications chưa đọc</li>
 *   <li>{@code (reference_id, type)} – idempotency check</li>
 *   <li>{@code (email_sent, retry_count)} – tìm notifications cần retry email</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id",       columnList = "user_id, created_at"),
    @Index(name = "idx_notifications_user_unread",   columnList = "user_id, read"),
    @Index(name = "idx_notifications_ref_type",      columnList = "reference_id, type"),
    @Index(name = "idx_notifications_email_retry",   columnList = "email_sent, retry_count")
})
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "reference_id", length = 36)
    private String referenceId;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent;

    @Column(name = "sse_pushed", nullable = false)
    private boolean ssePushed;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "email_failure_reason", length = 500)
    private String emailFailureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}