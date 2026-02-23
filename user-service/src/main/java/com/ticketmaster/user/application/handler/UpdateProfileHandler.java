package com.ticketmaster.user.application.handler;

import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.user.application.command.UpdateProfileCommand;
import com.ticketmaster.user.domain.model.User;
import com.ticketmaster.user.domain.repository.UserRepository;
import com.ticketmaster.user.infrastructure.persistence.mapper.UserMapper;
import com.ticketmaster.user.interfaces.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case cập nhật hồ sơ cá nhân.
 *
 * <p><b>Luồng xử lý:</b>
 * <ol>
 *   <li>Tìm User theo ID (từ JWT token)</li>
 *   <li>Gọi domain method {@link User#updateProfile} để tạo User mới với profile đã cập nhật</li>
 *   <li>Lưu lại vào DB</li>
 *   <li>Trả về UserResponse đã cập nhật</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateProfileHandler {

    private final UserRepository userRepository;
    private final UserMapper     userMapper;

    /**
     * Thực thi use case cập nhật profile.
     *
     * @param command command chứa userId và thông tin mới
     * @return UserResponse với thông tin đã cập nhật
     * @throws ResourceNotFoundException nếu không tìm thấy user
     */
    @Transactional
    public UserResponse handle(UpdateProfileCommand command) {
        log.info("[PROFILE] Updating profile for userId={}", command.getUserId());

        // 1. Tìm user
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", command.getUserId()));

        // 2. Cập nhật profile qua domain method (immutable)
        User updatedUser = user.updateProfile(
                command.getFullName(),
                command.getPhoneNumber()
        );

        // 3. Lưu lại
        User savedUser = userRepository.save(updatedUser);

        log.info("[PROFILE] Profile updated successfully for userId={}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }
}