package com.ticketmaster.user.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Response DTO cho thông tin user.
 * KHÔNG bao giờ chứa {@code passwordHash} hay các thông tin nhạy cảm khác.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private final String  id;
    private final String  email;
    private final String  role;
    private final String  fullName;
    private final String  phoneNumber;
    private final String  avatarUrl;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
}