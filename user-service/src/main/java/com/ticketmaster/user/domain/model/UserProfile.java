package com.ticketmaster.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

/**
 * Entity chứa thông tin hồ sơ cá nhân của User.
 *
 * <p>Được nhúng trực tiếp vào {@link User} aggregate (không có bảng riêng),
 * nhưng tách thành class riêng để:
 * <ul>
 *   <li>Dễ update profile mà không đụng vào credentials của User</li>
 *   <li>Đóng gói validation logic của từng field (phone format, v.v.)</li>
 * </ul>
 *
 * <p><b>Immutable bằng {@code @With}</b>: mọi thay đổi tạo ra instance mới,
 * đảm bảo User aggregate luôn kiểm soát được trạng thái của mình.
 */
@Getter
@Builder
@With
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    /** Tên hiển thị (vd: "Nguyen Van A"). Không phải tên đăng nhập. */
    private String fullName;

    /** Số điện thoại – optional, format quốc tế khuyến nghị (vd: "+84901234567"). */
    private String phoneNumber;

    /** URL avatar – optional. Lưu path trên S3 hoặc URL public. */
    private String avatarUrl;

    // ── Domain Methods ───────────────────────────────────────────

    /**
     * Kiểm tra profile có đủ thông tin cơ bản không.
     *
     * @return {@code true} nếu fullName không rỗng
     */
    public boolean isComplete() {
        return fullName != null && !fullName.isBlank();
    }

    /**
     * Tạo UserProfile với thông tin mới (immutable update).
     */
    public UserProfile updateWith(String fullName, String phoneNumber) {
        return this.withFullName(fullName).withPhoneNumber(phoneNumber);
    }
}