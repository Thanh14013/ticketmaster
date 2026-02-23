package com.ticketmaster.user.infrastructure.security;

import com.ticketmaster.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} implementation.
 *
 * <p>Được Spring Security gọi tự động khi cần load thông tin user để xác thực.
 * Dùng email làm "username" (theo Spring Security convention).
 *
 * <p>Được inject vào {@link SecurityConfig} thông qua {@link org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder}.
 *
 * <p>Lưu ý: User-service tự xác thực user (đây là nơi issue JWT).
 * Các service khác KHÔNG gọi class này – họ chỉ validate JWT token tại API Gateway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Load UserDetails theo email (username trong hệ thống này là email).
     *
     * @param email địa chỉ email người dùng
     * @return UserDetails cho Spring Security
     * @throws UsernameNotFoundException nếu email không tồn tại
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("[SECURITY] Loading user by email: {}", email);

        return userRepository.findByEmail(email)
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPasswordHash())
                        .authorities(List.of(new SimpleGrantedAuthority(user.getRole().name())))
                        .accountExpired(false)
                        .accountLocked(!user.isActive())
                        .credentialsExpired(false)
                        .disabled(!user.isActive())
                        .build()
                )
                .orElseThrow(() -> {
                    log.warn("[SECURITY] User not found: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });
    }
}