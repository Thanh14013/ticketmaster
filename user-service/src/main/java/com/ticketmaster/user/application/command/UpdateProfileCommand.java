package com.ticketmaster.user.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * Command object cho use case cập nhật hồ sơ cá nhân.
 */
@Getter
@Builder
public class UpdateProfileCommand {

    /** ID của user cần cập nhật (lấy từ JWT token qua X-User-Id header). */
    private final String userId;

    /** Tên đầy đủ mới. */
    private final String fullName;

    /** Số điện thoại mới – nullable. */
    private final String phoneNumber;
}