package com.ticketmaster.user.application.service;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.user.application.command.LoginCommand;
import com.ticketmaster.user.application.command.RegisterUserCommand;
import com.ticketmaster.user.application.command.UpdateProfileCommand;
import com.ticketmaster.user.application.handler.LoginHandler;
import com.ticketmaster.user.application.handler.RegisterUserHandler;
import com.ticketmaster.user.application.handler.UpdateProfileHandler;
import com.ticketmaster.user.domain.repository.UserRepository;
import com.ticketmaster.user.infrastructure.persistence.mapper.UserMapper;
import com.ticketmaster.user.interfaces.dto.AuthResponse;
import com.ticketmaster.user.interfaces.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service – Facade điều phối các handlers và queries.
 *
 * <p>Controllers trong interfaces layer chỉ gọi class này, không gọi handlers trực tiếp.
 * Điều này giúp:
 * <ul>
 *   <li>Giữ controllers mỏng (thin controllers)</li>
 *   <li>Dễ thêm cross-cutting concerns (cache, logging, events) tại một chỗ</li>
 *   <li>Dễ test application logic độc lập với HTTP layer</li>
 * </ul>
 *
 * <p><b>Cache strategy:</b>
 * <ul>
 *   <li>{@code users} cache: lưu UserResponse theo userId, TTL 1 giờ (từ application.yml)</li>
 *   <li>Evict khi profile được cập nhật</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final RegisterUserHandler registerUserHandler;
    private final LoginHandler        loginHandler;
    private final UpdateProfileHandler updateProfileHandler;
    private final UserRepository      userRepository;
    private final UserMapper          userMapper;

    // ── Commands ─────────────────────────────────────────────────

    /**
     * Đăng ký tài khoản mới.
     */
    public UserResponse register(RegisterUserCommand command) {
        return registerUserHandler.handle(command);
    }

    /**
     * Đăng nhập và trả về tokens.
     */
    public AuthResponse login(LoginCommand command) {
        return loginHandler.handle(command);
    }

    /**
     * Cập nhật hồ sơ cá nhân và xóa cache.
     */
    @CacheEvict(value = "users", key = "#command.userId")
    public UserResponse updateProfile(UpdateProfileCommand command) {
        return updateProfileHandler.handle(command);
    }

    // ── Queries ───────────────────────────────────────────────────

    /**
     * Lấy thông tin user theo ID – kết quả được cache theo userId.
     *
     * @param userId ID của user
     * @return UserResponse
     * @throws ResourceNotFoundException nếu user không tồn tại
     */
    @Cacheable(value = "users", key = "#userId")
    @Transactional(readOnly = true)
    public UserResponse getUserById(String userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}