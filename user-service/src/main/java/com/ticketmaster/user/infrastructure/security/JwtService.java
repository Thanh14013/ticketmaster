package com.ticketmaster.user.infrastructure.security;

import com.ticketmaster.common.security.JwtUtils;
import com.ticketmaster.user.domain.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service wrapper xung quanh {@link JwtUtils} của common-lib,
 * cung cấp API thuận tiện cho user-service.
 *
 * <p>Lý do có class riêng thay vì inject {@link JwtUtils} trực tiếp:
 * <ul>
 *   <li>Tách biệt "biết cách tạo token" với "biết User domain object"</li>
 *   <li>Dễ mock trong test của handlers</li>
 *   <li>Expose {@code getAccessTokenExpirationMs()} cho LoginHandler</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtUtils jwtUtils;

    @Getter
    @Value("${jwt.expiration-ms:86400000}")
    private long accessTokenExpirationMs;

    /**
     * Tạo JWT access token từ User aggregate.
     * Token chứa: sub=userId, email, role.
     *
     * @param user User đã được xác thực
     * @return JWT access token string
     */
    public String generateAccessToken(User user) {
        return jwtUtils.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    /**
     * Tạo JWT refresh token từ User aggregate.
     * Token chỉ chứa: sub=userId (không có extra claims).
     *
     * @param user User đã được xác thực
     * @return JWT refresh token string
     */
    public String generateRefreshToken(User user) {
        return jwtUtils.generateRefreshToken(user.getId());
    }

    /**
     * Extract userId từ refresh token để issue access token mới.
     *
     * @param refreshToken refresh token string
     * @return userId (UUID)
     */
    public String extractUserIdFromRefreshToken(String refreshToken) {
        return jwtUtils.extractUserId(refreshToken);
    }

    /**
     * Validate token hợp lệ (chữ ký đúng, chưa hết hạn).
     *
     * @param token JWT token string
     * @return true nếu hợp lệ
     */
    public boolean isValidToken(String token) {
        return jwtUtils.validateToken(token);
    }
}