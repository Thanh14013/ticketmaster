package com.ticketmaster.user.domain.service;

import com.ticketmaster.user.domain.model.User;
import com.ticketmaster.user.domain.repository.UserRepository;
import com.ticketmaster.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Domain Service chứa business logic liên quan đến User nhưng không thuộc về
 * một aggregate cụ thể, hoặc cần phối hợp với infrastructure (password encoding).
 *
 * <p><b>Khi nào dùng Domain Service thay vì Aggregate Method?</b>
 * <ul>
 *   <li>Logic cần access Repository (vd: kiểm tra email duplicate)</li>
 *   <li>Logic cần infrastructure dependency (vd: PasswordEncoder)</li>
 *   <li>Logic liên quan đến nhiều aggregate</li>
 * </ul>
 *
 * <p>Application handlers ({@link com.ticketmaster.user.application.handler.RegisterUserHandler})
 * gọi Domain Service này, không gọi Repository trực tiếp.
 */
@Service
@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Password Operations ───────────────────────────────────────

    /**
     * Hash plain text password bằng bcrypt.
     * Delegated sang PasswordEncoder để dễ thay đổi algorithm sau.
     *
     * @param rawPassword plain text password
     * @return bcrypt hashed password
     */
    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Kiểm tra plain text password khớp với hashed password trong DB.
     *
     * @param rawPassword    plain text password từ login request
     * @param hashedPassword hashed password lưu trong DB
     * @return {@code true} nếu password khớp
     */
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    // ── Business Validation ───────────────────────────────────────

    /**
     * Validate email chưa được đăng ký trong hệ thống.
     * Ném exception nếu email đã tồn tại.
     *
     * @param email email cần kiểm tra
     * @throws BusinessException 409 Conflict nếu email đã được sử dụng
     */
    public void validateEmailNotTaken(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    "Email '" + email + "' is already registered",
                    "EMAIL_ALREADY_EXISTS",
                    HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Validate user tồn tại và có thể đăng nhập.
     * Ném exception nếu không tìm thấy hoặc tài khoản bị khóa.
     *
     * @param user User aggregate cần kiểm tra
     * @throws BusinessException 401 nếu tài khoản bị vô hiệu hóa
     */
    public void validateUserCanLogin(User user) {
        if (!user.canLogin()) {
            throw new BusinessException(
                    "Account has been deactivated. Please contact support.",
                    "ACCOUNT_DEACTIVATED",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    /**
     * Validate credentials (email + password) trong quá trình đăng nhập.
     * Dùng timing-safe comparison để tránh timing attack.
     *
     * @param rawPassword    password từ request
     * @param hashedPassword password trong DB
     * @throws BusinessException 401 nếu credentials sai
     */
    public void validateCredentials(String rawPassword, String hashedPassword) {
        if (!verifyPassword(rawPassword, hashedPassword)) {
            // Không nói rõ "sai email" hay "sai password" để tránh user enumeration attack
            throw new BusinessException(
                    "Invalid email or password",
                    "INVALID_CREDENTIALS",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }
}