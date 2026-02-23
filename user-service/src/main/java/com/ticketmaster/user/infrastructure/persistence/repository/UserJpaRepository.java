package com.ticketmaster.user.infrastructure.persistence.repository;

import com.ticketmaster.user.domain.model.User;
import com.ticketmaster.user.domain.repository.UserRepository;
import com.ticketmaster.user.infrastructure.persistence.entity.UserJpaEntity;
import com.ticketmaster.user.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Infrastructure implementation của {@link UserRepository} (Domain interface).
 *
 * <p>Pattern: Adapter giữa domain và Spring Data JPA.
 * Class này:
 * <ul>
 *   <li>Implements {@link UserRepository} (domain interface) → thỏa mãn Dependency Inversion</li>
 *   <li>Inject {@link SpringDataUserRepository} (Spring Data JPA) để thực hiện queries</li>
 *   <li>Dùng {@link UserMapper} để convert giữa domain model và JPA entity</li>
 * </ul>
 *
 * <p>Domain layer gọi {@link UserRepository} interface – không biết class này tồn tại.
 */
@Repository
@RequiredArgsConstructor
public class UserJpaRepository implements UserRepository {

    private final SpringDataUserRepository springDataRepository;
    private final UserMapper               userMapper;

    @Override
    public User save(User user) {
        UserJpaEntity entity  = userMapper.toEntity(user);
        UserJpaEntity saved   = springDataRepository.save(entity);
        return userMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(String id) {
        return springDataRepository.findById(id)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springDataRepository.findByEmail(email)
                .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataRepository.existsByEmail(email);
    }
}

/**
 * Spring Data JPA repository – chỉ làm việc với JPA Entity, không expose ra ngoài package.
 * Interface này là implementation detail của infrastructure layer.
 */
interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, String> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}