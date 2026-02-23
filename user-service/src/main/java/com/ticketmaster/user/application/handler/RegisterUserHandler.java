package com.ticketmaster.user.application.handler;

import com.ticketmaster.common.util.IdGenerator;
import com.ticketmaster.user.application.command.RegisterUserCommand;
import com.ticketmaster.user.domain.model.User;
import com.ticketmaster.user.domain.repository.UserRepository;
import com.ticketmaster.user.domain.service.UserDomainService;
import com.ticketmaster.user.interfaces.dto.UserResponse;
import com.ticketmaster.user.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler cho use case đăng ký tài khoản mới.
 *
 * <p><b>Luồng xử lý (RegisterUserCommand → UserResponse):</b>
 * <ol>
 *   <li>Validate email chưa tồn tại trong hệ thống</li>
 *   <li>Hash password bằng bcrypt</li>
 *   <li>Tạo User aggregate với factory method {@link User#create}</li>
 *   <li>Lưu vào DB qua Repository</li>
 *   <li>Trả về UserResponse (không có password)</li>
 * </ol>
 *
 * <p>Handler này KHÔNG biết HTTP, request/response hay Spring MVC.
 * Sự tách biệt này cho phép test thuần túy mà không cần mock HTTP context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUserHandler {

    private final UserDomainService userDomainService;
    private final UserRepository    userRepository;
    private final UserMapper        userMapper;

    /**
     * Thực thi use case đăng ký tài khoản.
     *
     * @param command command chứa thông tin đăng ký
     * @return UserResponse thông tin user vừa tạo (không có sensitive data)
     * @throws com.ticketmaster.common.exception.BusinessException nếu email đã tồn tại
     */
    @Transactional
    public UserResponse handle(RegisterUserCommand command) {
        log.info("[REGISTER] Attempting to register new user with email: {}", command.getEmail());

        // 1. Validate email chưa được đăng ký
        userDomainService.validateEmailNotTaken(command.getEmail());

        // 2. Hash password
        String hashedPassword = userDomainService.hashPassword(command.getPassword());

        // 3. Tạo User aggregate
        User user = User.create(
                IdGenerator.newId(),
                command.getEmail(),
                hashedPassword,
                command.getFullName()
        );

        // 4. Lưu vào DB
        User savedUser = userRepository.save(user);

        log.info("[REGISTER] Successfully registered user id={} email={}",
                savedUser.getId(), savedUser.getEmail());

        // 5. Map sang response DTO
        return userMapper.toResponse(savedUser);
    }
}