package com.ticketmaster.user.infrastructure.persistence.entity;

import com.ticketmaster.user.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA Entity mapping với bảng {@code users} trong PostgreSQL.
 *
 * <p><b>DDD Rule:</b> Class này CHỈ tồn tại ở Infrastructure layer.
 * Domain layer không biết class này. Mapping qua lại với domain model
 * {@link com.ticketmaster.user.domain.model.User} được thực hiện bởi
 * {@link UserMapper} (MapStruct).
 *
 * <p>Schema được quản lý bởi Liquibase ({@code V001__create_users_table.xml}),
 * không phải JPA auto-DDL ({@code ddl-auto=validate}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class UserJpaEntity {

    /** UUID v4, sinh bởi application (không phải DB auto-increment). */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    /** Email – unique, dùng làm định danh đăng nhập. */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Bcrypt hashed password. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    /** Vai trò người dùng – lưu dưới dạng String. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /** Tên đầy đủ. */
    @Column(name = "full_name", length = 200)
    private String fullName;

    /** Số điện thoại – optional. */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /** URL avatar – optional. */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Trạng thái tài khoản. */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** Thời điểm tạo (UTC). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Thời điểm cập nhật gần nhất (UTC). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}