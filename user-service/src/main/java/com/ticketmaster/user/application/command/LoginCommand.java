package com.ticketmaster.user.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * Command object cho use case đăng nhập.
 */
@Getter
@Builder
public class LoginCommand {

    /** Email tài khoản. */
    private final String email;

    /** Plain text password cần verify. */
    private final String password;
}