package com.ticketmaster.payment.domain.repository;

import com.ticketmaster.payment.domain.model.Transaction;
import com.ticketmaster.payment.domain.model.TransactionStatus;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository interface cho {@link Transaction} aggregate.
 * Implementation tại infrastructure layer.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(String id);

    /**
     * Tìm transaction theo bookingId.
     * Dùng để kiểm tra idempotency (tránh xử lý payment 2 lần cho cùng booking).
     */
    Optional<Transaction> findByBookingId(String bookingId);

    /**
     * Tìm transaction thành công của booking (dùng khi refund).
     */
    Optional<Transaction> findSucceededByBookingId(String bookingId);

    /**
     * Lấy lịch sử transaction của user.
     */
    List<Transaction> findByUserId(String userId);

    boolean existsByBookingId(String bookingId);
}