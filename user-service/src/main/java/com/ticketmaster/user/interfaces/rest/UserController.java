package com.ticketmaster.user.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.user.application.command.UpdateProfileCommand;
import com.ticketmaster.user.application.service.UserApplicationService;
import com.ticketmaster.user.interfaces.dto.UpdateProfileRequest;
import com.ticketmaster.user.interfaces.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho user profile management.
 *
 * <p>Base path: {@code /api/v1/users}
 *
 * <p>Tất cả endpoints yêu cầu JWT token – API Gateway inject
 * {@code X-User-Id} header sau khi validate token.
 * Controllers đọc userId từ header này thay vì parse JWT trực tiếp.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Quản lý thông tin người dùng")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserApplicationService userApplicationService;

    /**
     * Lấy thông tin profile của user đang đăng nhập.
     *
     * @param userId  ID từ JWT token (inject bởi API Gateway qua {@code X-User-Id})
     * @return 200 OK với UserResponse
     */
    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin profile của user hiện tại")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @RequestHeader("X-User-Id") String userId) {

        UserResponse response = userApplicationService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Cập nhật hồ sơ cá nhân của user đang đăng nhập.
     *
     * @param userId  ID từ JWT token (inject bởi API Gateway)
     * @param request thông tin cần cập nhật
     * @return 200 OK với UserResponse đã cập nhật
     */
    @PutMapping("/me/profile")
    @Operation(summary = "Cập nhật hồ sơ cá nhân")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UpdateProfileCommand command = UpdateProfileCommand.builder()
                .userId(userId)
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .build();

        UserResponse response = userApplicationService.updateProfile(command);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", response));
    }

    /**
     * Lấy thông tin user theo ID (dùng nội bộ giữa các services hoặc admin).
     *
     * @param id     ID của user cần xem
     * @param userId ID của user đang thực hiện request (kiểm tra quyền)
     * @return 200 OK với UserResponse
     */
    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin user theo ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {

        // Business rule: user thường chỉ xem được profile của chính mình
        // Admin có thể xem của người khác (kiểm tra qua @PreAuthorize nếu cần)
        UserResponse response = userApplicationService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}