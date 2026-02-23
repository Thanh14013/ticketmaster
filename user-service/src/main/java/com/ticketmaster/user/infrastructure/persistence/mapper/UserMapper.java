package com.ticketmaster.user.infrastructure.persistence.mapper;

import com.ticketmaster.user.domain.model.User;
import com.ticketmaster.user.domain.model.UserProfile;
import com.ticketmaster.user.infrastructure.persistence.entity.UserJpaEntity;
import com.ticketmaster.user.interfaces.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct Mapper chuyển đổi giữa:
 * <ul>
 *   <li>{@link User} (Domain Model) ↔ {@link UserJpaEntity} (JPA Entity)</li>
 *   <li>{@link User} (Domain Model) → {@link UserResponse} (Response DTO)</li>
 * </ul>
 *
 * <p>MapStruct generate implementation tại compile-time → zero reflection overhead.
 * {@code componentModel = "spring"} → bean được đăng ký vào Spring context tự động.
 *
 * <p><b>Quy tắc mapping đặc biệt:</b>
 * <ul>
 *   <li>Domain {@link User} có nested {@link UserProfile} →
 *       flat sang {@link UserJpaEntity} (fullName, phoneNumber, avatarUrl)</li>
 *   <li>Ngược lại: flat entity fields → nested UserProfile khi convert về domain</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    // ── Domain → JPA Entity ──────────────────────────────────────

    /**
     * Chuyển User domain model sang JPA entity để lưu DB.
     * Unpack nested UserProfile thành flat fields.
     */
    @Mapping(target = "fullName",    source = "profile.fullName")
    @Mapping(target = "phoneNumber", source = "profile.phoneNumber")
    @Mapping(target = "avatarUrl",   source = "profile.avatarUrl")
    UserJpaEntity toEntity(User user);

    // ── JPA Entity → Domain ──────────────────────────────────────

    /**
     * Chuyển JPA entity sang User domain model.
     * Pack flat fields thành nested UserProfile.
     */
    @Mapping(target = "profile", source = ".", qualifiedByName = "toUserProfile")
    User toDomain(UserJpaEntity entity);

    /**
     * Build UserProfile từ flat JPA entity fields.
     */
    @Named("toUserProfile")
    default UserProfile toUserProfile(UserJpaEntity entity) {
        return UserProfile.builder()
                .fullName(entity.getFullName())
                .phoneNumber(entity.getPhoneNumber())
                .avatarUrl(entity.getAvatarUrl())
                .build();
    }

    // ── Domain → Response DTO ────────────────────────────────────

    /**
     * Chuyển User domain model sang response DTO (không có sensitive data).
     * Password hash KHÔNG bao giờ xuất hiện trong response.
     */
    @Mapping(target = "fullName",    source = "profile.fullName")
    @Mapping(target = "phoneNumber", source = "profile.phoneNumber")
    @Mapping(target = "avatarUrl",   source = "profile.avatarUrl")
    UserResponse toResponse(User user);
}