package com.ticketmaster.user.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.user.application.command.LoginCommand;
import com.ticketmaster.user.application.command.RegisterUserCommand;
import com.ticketmaster.user.application.service.UserApplicationService;
import com.ticketmaster.user.interfaces.dto.AuthResponse;
import com.ticketmaster.user.interfaces.dto.LoginRequest;
import com.ticketmaster.user.interfaces.dto.RegisterRequest;
import com.ticketmaster.user.interfaces.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho authentication endpoints.
 *
 * <p>Base path: {@code /api/v1/auth}
 *
 * <p>Tất cả endpoints này là PUBLIC (không cần JWT token),
 * được cấu hình trong {@link com.ticketmaster.user.infrastructure.security.SecurityConfig}.
 * Rate limiting (10 req/phút) được áp dụng tại API Gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, refresh token")
public class AuthController {

    private final UserApplicationService userApplicationService;

    /**
     * Đăng ký tài khoản mới.
     *
     * @param request thông tin đăng ký (email, password, fullName)
     * @return 201 Created với UserResponse
     */
    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản mới")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterUserCommand command = RegisterUserCommand.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .fullName(request.getFullName())
                .build();

        UserResponse response = userApplicationService.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * Đăng nhập và lấy JWT tokens.
     *
     * @param request thông tin đăng nhập (email, password)
     * @return 200 OK với AuthResponse chứa accessToken và refreshToken
     */
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập lấy JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginCommand command = LoginCommand.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .build();

        AuthResponse response = userApplicationService.login(command);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}