package com.ticketmaster.payment.infrastructure.persistence.repository;

import com.ticketmaster.payment.domain.model.Transaction;
import com.ticketmaster.payment.domain.model.TransactionStatus;
import com.ticketmaster.payment.domain.repository.TransactionRepository;
import com.ticketmaster.payment.infrastructure.persistence.entity.TransactionJpaEntity;
import com.ticketmaster.payment.infrastructure.persistence.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter implementation của {@link TransactionRepository} (Domain interface).
 */
@Repository
@RequiredArgsConstructor
public class TransactionJpaRepository implements TransactionRepository {

    private final SpringDataTransactionRepository springDataRepository;
    private final TransactionMapper               transactionMapper;

    @Override
    public Transaction save(Transaction transaction) {
        return transactionMapper.toDomain(
                springDataRepository.save(transactionMapper.toEntity(transaction)));
    }

    @Override
    public Optional<Transaction> findById(String id) {
        return springDataRepository.findById(id).map(transactionMapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByBookingId(String bookingId) {
        return springDataRepository.findByBookingId(bookingId)
                .map(transactionMapper::toDomain);
    }

    @Override
    public Optional<Transaction> findSucceededByBookingId(String bookingId) {
        return springDataRepository
                .findByBookingIdAndStatus(bookingId, TransactionStatus.SUCCEEDED)
                .map(transactionMapper::toDomain);
    }

    @Override
    public List<Transaction> findByUserId(String userId) {
        return springDataRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByBookingId(String bookingId) {
        return springDataRepository.existsByBookingId(bookingId);
    }
}

/**
 * Spring Data JPA inner interface – không expose ra ngoài package.
 */
interface SpringDataTransactionRepository extends JpaRepository<TransactionJpaEntity, String> {
    Optional<TransactionJpaEntity> findByBookingId(String bookingId);
    Optional<TransactionJpaEntity> findByBookingIdAndStatus(String bookingId, TransactionStatus status);
    List<TransactionJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    boolean existsByBookingId(String bookingId);
}