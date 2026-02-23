package com.ticketmaster.user.domain.repository;

import com.ticketmaster.user.domain.model.User;

import java.util.Optional;

/**
 * Domain Repository interface cho {@link User} aggregate.
 *
 * <p><b>DDD Rule:</b> Interface này nằm ở Domain layer, implementation
 * ({@link com.ticketmaster.user.infrastructure.persistence.repository.UserJpaRepository})
 * nằm ở Infrastructure layer.
 *
 * <p>Domain layer hoàn toàn KHÔNG biết JPA, SQL hay bất kỳ persistence technology nào.
 * Dependency luôn chạy từ Infrastructure → Domain, không bao giờ ngược lại.
 *
 * <p>Chỉ định nghĩa các query thực sự cần thiết cho business logic.
 * Tránh tạo quá nhiều finder methods (YAGNI principle).
 */
public interface UserRepository {

    /**
     * Lưu (create hoặc update) một User aggregate.
     *
     * @param user aggregate cần lưu
     * @return User đã được lưu (có thể có thêm dữ liệu từ DB như auto-generated fields)
     */
    User save(User user);

    /**
     * Tìm User theo ID.
     *
     * @param id UUID của user
     * @return Optional chứa User nếu tìm thấy
     */
    Optional<User> findById(String id);

    /**
     * Tìm User theo email (dùng cho đăng nhập và kiểm tra duplicate).
     *
     * @param email địa chỉ email
     * @return Optional chứa User nếu tìm thấy
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra email đã tồn tại trong hệ thống chưa.
     * Dùng trong RegisterUserHandler để validate trước khi tạo tài khoản.
     *
     * @param email địa chỉ email cần kiểm tra
     * @return {@code true} nếu email đã được đăng ký
     */
    boolean existsByEmail(String email);
}