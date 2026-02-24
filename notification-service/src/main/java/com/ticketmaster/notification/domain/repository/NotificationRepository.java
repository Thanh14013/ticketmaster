package com.ticketmaster.notification.domain.repository;

import com.ticketmaster.notification.domain.model.Notification;
import com.ticketmaster.notification.domain.model.NotificationType;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository interface cho {@link Notification} aggregate.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    /** Lấy tất cả notifications của user, sắp xếp mới nhất trước. */
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Lấy notifications chưa đọc của user. */
    List<Notification> findUnreadByUserId(String userId);

    /** Đếm số notifications chưa đọc của user. */
    long countUnreadByUserId(String userId);

    /** Lấy notifications theo referenceId (vd: bookingId). */
    List<Notification> findByReferenceId(String referenceId);

    /** Lấy notifications gửi email thất bại và còn có thể retry. */
    List<Notification> findEmailFailedAndCanRetry();

    /** Kiểm tra đã gửi notification cho reference này chưa (idempotency). */
    boolean existsByReferenceIdAndType(String referenceId, NotificationType type);
}