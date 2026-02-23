package com.ticketmaster.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User – Aggregate Root của bounded context Identity & Access.
 *
 * <p><b>Quy tắc DDD quan trọng:</b>
 * <ul>
 *   <li>Class này là PURE JAVA – không có annotation của Spring, JPA, hay framework nào</li>
 *   <li>Tất cả business invariants được bảo vệ tại đây (không phải tại service layer)</li>
 *   <li>Chỉ có thể thay đổi trạng thái qua các domain method (activate, deactivate, v.v.)</li>
 *   <li>Mapping sang JPA Entity xảy ra tại infrastructure layer ({@link infrastructure.persistence.mapper.UserMapper})</li>
 * </ul>
 *
 * <p><b>Invariants:</b>
 * <ul>
 *   <li>Email không thể thay đổi sau khi đăng ký (identity)</li>
 *   <li>Password luôn là hashed bcrypt (không bao giờ plain text)</li>
 *   <li>User mới luôn bắt đầu với role {@link UserRole#ROLE_USER} và {@code active = true}</li>
 * </ul>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** UUID v4 – primary key, sinh bởi {@link com.ticketmaster.common.util.IdGenerator}. */
    private String id;

    /**
     * Email dùng làm định danh đăng nhập – không thay đổi được sau khi tạo.
     * Phải unique trong hệ thống (enforced bởi DB unique constraint).
     */
    private String email;

    /**
     * Bcrypt-hashed password. KHÔNG BAO GIỜ lưu plain text.
     * Hashing xảy ra trong {@link application.handler.RegisterUserHandler}.
     */
    private String passwordHash;

    /** Role phân quyền trong hệ thống. */
    private UserRole role;

    /** Thông tin hồ sơ cá nhân – có thể cập nhật được. */
    private UserProfile profile;

    /** {@code true}: user có thể đăng nhập. {@code false}: bị ban/deactivated. */
    private boolean active;

    /** Thời điểm tạo tài khoản (UTC). */
    private Instant createdAt;

    /** Thời điểm cập nhật gần nhất (UTC). */
    private Instant updatedAt;

    // ── Factory Method ───────────────────────────────────────────

    /**
     * Tạo User mới từ thông tin đăng ký.
     * Đảm bảo các giá trị mặc định đúng (role=USER, active=true).
     *
     * @param id           UUID đã sinh sẵn
     * @param email        email người dùng
     * @param passwordHash bcrypt hash của mật khẩu
     * @param fullName     tên đầy đủ
     * @return User aggregate mới
     */
    public static User create(String id, String email, String passwordHash, String fullName) {
        Instant now = Instant.now();
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordHash)
                .role(UserRole.ROLE_USER)
                .profile(UserProfile.builder().fullName(fullName).build())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ── Domain Methods ───────────────────────────────────────────

    /**
     * Cập nhật hồ sơ cá nhân.
     * Tạo instance User mới với profile đã cập nhật (không mutate).
     *
     * @param fullName    tên mới
     * @param phoneNumber số điện thoại mới (nullable)
     * @return User mới với profile đã cập nhật
     */
    public User updateProfile(String fullName, String phoneNumber) {
        UserProfile updatedProfile = this.profile.updateWith(fullName, phoneNumber);
        return User.builder()
                .id(this.id)
                .email(this.email)
                .passwordHash(this.passwordHash)
                .role(this.role)
                .profile(updatedProfile)
                .active(this.active)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Vô hiệu hóa tài khoản (soft ban).
     *
     * @return User mới với active=false
     */
    public User deactivate() {
        return User.builder()
                .id(this.id)
                .email(this.email)
                .passwordHash(this.passwordHash)
                .role(this.role)
                .profile(this.profile)
                .active(false)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Kiểm tra user có thể đăng nhập không.
     *
     * @return {@code true} nếu tài khoản đang hoạt động
     */
    public boolean canLogin() {
        return this.active;
    }

    /**
     * Kiểm tra user có quyền admin không.
     *
     * @return {@code true} nếu role là ROLE_ADMIN
     */
    public boolean isAdmin() {
        return UserRole.ROLE_ADMIN.equals(this.role);
    }
}