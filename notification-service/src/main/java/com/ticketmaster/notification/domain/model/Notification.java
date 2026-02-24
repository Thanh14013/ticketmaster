package com.ticketmaster.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Notification – Aggregate Root của bounded context Notification.
 *
 * <p>Đại diện cho một notification đã được gửi hoặc đang chờ gửi.
 * Lưu lịch sử đầy đủ để:
 * <ul>
 *   <li>Hiển thị notification history trong client</li>
 *   <li>Retry khi email gửi thất bại</li>
 *   <li>Audit / compliance</li>
 * </ul>
 *
 * <p><b>Channels:</b> Một notification có thể gửi qua nhiều kênh:
 * <ul>
 *   <li>EMAIL – qua JavaMailSender + Thymeleaf template</li>
 *   <li>SSE   – push real-time về browser client đang kết nối</li>
 * </ul>
 *
 * <p><b>Pure Java:</b> Không có annotation Spring hay JPA.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    /** UUID v4 – primary key. */
    private String           id;

    /** ID user nhận notification. */
    private String           userId;

    /** Email recipient (snapshot). */
    private String           recipientEmail;

    /** Loại notification – xác định template và nội dung. */
    private NotificationType type;

    /** Tiêu đề notification (email subject / SSE title). */
    private String           title;

    /** Nội dung notification dạng text thuần (fallback). */
    private String           message;

    /**
     * Reference ID – liên kết đến entity gốc.
     * Ví dụ: bookingId, transactionId, eventId.
     */
    private String           referenceId;

    /**
     * Loại entity của referenceId.
     * Ví dụ: "BOOKING", "TRANSACTION", "EVENT".
     */
    private String           referenceType;

    /** true nếu email đã gửi thành công. */
    private boolean          emailSent;

    /** true nếu đã push qua SSE. */
    private boolean          ssePushed;

    /** true nếu user đã đọc (SSE notification). */
    private boolean          read;

    /** Lý do gửi email thất bại (null nếu thành công). */
    private String           emailFailureReason;

    /** Số lần retry gửi email (tối đa 3). */
    private int              retryCount;

    /** Thời điểm tạo (UTC). */
    private Instant          createdAt;

    /** Thời điểm cập nhật (UTC). */
    private Instant          updatedAt;

    // ── Factory Method ─────────────────────────────────────────────

    /**
     * Tạo Notification mới.
     *
     * @param id             UUID đã sinh sẵn
     * @param userId         ID user nhận notification
     * @param recipientEmail email người nhận
     * @param type           loại notification
     * @param title          tiêu đề
     * @param message        nội dung text
     * @param referenceId    ID entity liên quan (bookingId, transactionId)
     * @param referenceType  loại entity liên quan
     * @return Notification mới chưa được gửi
     */
    public static Notification create(String id, String userId, String recipientEmail,
                                       NotificationType type, String title, String message,
                                       String referenceId, String referenceType) {
        Instant now = Instant.now();
        return Notification.builder()
                .id(id)
                .userId(userId)
                .recipientEmail(recipientEmail)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .emailSent(false)
                .ssePushed(false)
                .read(false)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ── State Transition Methods ────────────────────────────────────

    /** Đánh dấu email đã gửi thành công. */
    public Notification markEmailSent() {
        return toBuilder()
                .emailSent(true)
                .emailFailureReason(null)
                .updatedAt(Instant.now())
                .build();
    }

    /** Đánh dấu gửi email thất bại và tăng retry count. */
    public Notification markEmailFailed(String reason) {
        return toBuilder()
                .emailSent(false)
                .emailFailureReason(reason)
                .retryCount(this.retryCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    /** Đánh dấu đã push SSE thành công. */
    public Notification markSsePushed() {
        return toBuilder()
                .ssePushed(true)
                .updatedAt(Instant.now())
                .build();
    }

    /** Đánh dấu user đã đọc notification. */
    public Notification markRead() {
        return toBuilder()
                .read(true)
                .updatedAt(Instant.now())
                .build();
    }

    // ── Query Methods ──────────────────────────────────────────────

    public boolean canRetryEmail() {
        return !emailSent && retryCount < 3;
    }

    // ── Builder Helper ─────────────────────────────────────────────
    private NotificationBuilder toBuilder() {
        return Notification.builder()
                .id(id).userId(userId).recipientEmail(recipientEmail)
                .type(type).title(title).message(message)
                .referenceId(referenceId).referenceType(referenceType)
                .emailSent(emailSent).ssePushed(ssePushed).read(read)
                .emailFailureReason(emailFailureReason).retryCount(retryCount)
                .createdAt(createdAt);
    }
}