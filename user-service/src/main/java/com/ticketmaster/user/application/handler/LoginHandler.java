package com.ticketmaster.user.application.handler;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.user.application.command.LoginCommand;
import com.ticketmaster.user.domain.model.User;
import com.ticketmaster.user.domain.repository.UserRepository;
import com.ticketmaster.user.domain.service.UserDomainService;
import com.ticketmaster.user.infrastructure.security.JwtService;
import com.ticketmaster.user.interfaces.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case đăng nhập – trả về JWT access token và refresh token.
 *
 * <p><b>Luồng xử lý (LoginCommand → AuthResponse):</b>
 * <ol>
 *   <li>Tìm User theo email</li>
 *   <li>Validate tài khoản đang active</li>
 *   <li>Verify password</li>
 *   <li>Generate access token (24h) và refresh token (7 ngày)</li>
 *   <li>Trả về AuthResponse với cả hai tokens</li>
 * </ol>
 *
 * <p>Security note: Không phân biệt "sai email" hay "sai password" trong error message
 * để ngăn chặn user enumeration attack.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginHandler {

    private final UserRepository    userRepository;
    private final UserDomainService userDomainService;
    private final JwtService        jwtService;

    /**
     * Thực thi use case đăng nhập.
     *
     * @param command command chứa email và password
     * @return AuthResponse với access token và refresh token
     * @throws com.ticketmaster.common.exception.BusinessException nếu credentials sai hoặc account locked
     */
    @Transactional(readOnly = true)
    public AuthResponse handle(LoginCommand command) {
        log.info("[LOGIN] Login attempt for email: {}", command.getEmail());

        // 1. Tìm user – nếu không thấy vẫn báo "Invalid credentials" (tránh enumeration)
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() ->
                        new com.ticketmaster.common.exception.BusinessException(
                                "Invalid email or password",
                                "INVALID_CREDENTIALS",
                                org.springframework.http.HttpStatus.UNAUTHORIZED
                        )
                );

        // 2. Validate account active
        userDomainService.validateUserCanLogin(user);

        // 3. Verify password
        userDomainService.validateCredentials(command.getPassword(), user.getPasswordHash());

        // 4. Generate tokens
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("[LOGIN] Login successful for userId={}", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}