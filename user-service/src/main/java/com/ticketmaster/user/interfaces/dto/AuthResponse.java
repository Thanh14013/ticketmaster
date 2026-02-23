package com.ticketmaster.user.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Response DTO cho login và refresh token APIs.
 * Chứa access token, refresh token và thông tin cơ bản của user.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    /** JWT Access Token – gửi trong mọi request tiếp theo dưới dạng {@code Bearer {token}}. */
    private final String accessToken;

    /** JWT Refresh Token – dùng để lấy access token mới khi hết hạn. */
    private final String refreshToken;

    /** Loại token – luôn là "Bearer". */
    private final String tokenType;

    /** Thời gian sống của access token (giây). */
    private final long expiresIn;

    /** ID của user đã đăng nhập. */
    private final String userId;

    /** Email của user. */
    private final String email;

    /** Role của user (vd: "ROLE_USER", "ROLE_ADMIN"). */
    private final String role;
}