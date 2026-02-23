package com.ticketmaster.user.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * Command object cho use case đăng ký tài khoản mới.
 *
 * <p>Command là plain data holder – không có logic, không có annotation framework.
 * Được tạo từ request DTO ở interfaces layer và truyền xuống application handler.
 *
 * <p><b>Bất biến (immutable):</b> dùng {@code @Builder} + {@code @Getter} mà không có setter,
 * đảm bảo command không bị thay đổi trong quá trình xử lý.
 */
@Getter
@Builder
public class RegisterUserCommand {

    /** Email đăng ký – sẽ dùng làm định danh đăng nhập. */
    private final String email;

    /** Plain text password – sẽ được hash trong handler. */
    private final String password;

    /** Tên đầy đủ hiển thị. */
    private final String fullName;
}