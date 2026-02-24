package com.ticketmaster.notification.infrastructure.persistence.repository;

import com.ticketmaster.notification.domain.model.Notification;
import com.ticketmaster.notification.domain.model.NotificationType;
import com.ticketmaster.notification.domain.repository.NotificationRepository;
import com.ticketmaster.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter implementation của {@link NotificationRepository} (Domain interface).
 *
 * <p>Pattern: Adapter giữa domain và Spring Data JPA.
 * Dùng inline MapStruct mapper để convert giữa domain ↔ JPA entity.
 */
@Repository
@RequiredArgsConstructor
public class NotificationJpaRepository implements NotificationRepository {

    private final SpringDataNotificationRepository springDataRepository;
    private final NotificationEntityMapper         mapper;

    @Override
    public Notification save(Notification notification) {
        return mapper.toDomain(
                springDataRepository.save(mapper.toEntity(notification)));
    }

    @Override
    public Optional<Notification> findById(String id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Notification> findByUserIdOrderByCreatedAtDesc(String userId) {
        return springDataRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Notification> findUnreadByUserId(String userId) {
        return springDataRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countUnreadByUserId(String userId) {
        return springDataRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    public List<Notification> findByReferenceId(String referenceId) {
        return springDataRepository.findByReferenceIdOrderByCreatedAtDesc(referenceId).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Notification> findEmailFailedAndCanRetry() {
        return springDataRepository.findEmailFailedAndCanRetry().stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean existsByReferenceIdAndType(String referenceId, NotificationType type) {
        return springDataRepository.existsByReferenceIdAndType(referenceId, type);
    }
}

// ── Spring Data Inner Interface ────────────────────────────────────────────────

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, String> {

    List<NotificationJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<NotificationJpaEntity> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);

    long countByUserIdAndReadFalse(String userId);

    List<NotificationJpaEntity> findByReferenceIdOrderByCreatedAtDesc(String referenceId);

    boolean existsByReferenceIdAndType(String referenceId, NotificationType type);

    /**
     * Tìm notifications gửi email thất bại và còn có thể retry (retryCount < 3).
     */
    @Query("""
        SELECT n FROM NotificationJpaEntity n
        WHERE n.emailSent = false
          AND n.retryCount < 3
        ORDER BY n.createdAt ASC
        """)
    List<NotificationJpaEntity> findEmailFailedAndCanRetry();
}

// ── MapStruct Mapper (inline) ──────────────────────────────────────────────────

@Mapper(componentModel = "spring")
interface NotificationEntityMapper {

    NotificationJpaEntity toEntity(Notification domain);

    Notification toDomain(NotificationJpaEntity entity);
}