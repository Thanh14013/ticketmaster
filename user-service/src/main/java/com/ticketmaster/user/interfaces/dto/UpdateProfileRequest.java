package com.ticketmaster.user.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request DTO cho API cập nhật hồ sơ cá nhân.
 * {@code PUT /api/v1/users/me/profile}
 */
@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 200, message = "Full name must be between 2 and 200 characters")
    private String fullName;

    @Pattern(
            regexp = "^\\+?[0-9\\s\\-()]{7,20}$",
            message = "Phone number format is invalid"
    )
    private String phoneNumber;
}