package com.ticketmaster.payment.domain.service;

import com.ticketmaster.common.exception.BusinessException;
import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.payment.domain.model.Transaction;
import com.ticketmaster.payment.domain.model.TransactionStatus;
import com.ticketmaster.payment.domain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Domain Service chứa business rules của Payment bounded context.
 *
 * <p>Điểm khác biệt so với các service khác: Payment domain cần đặc biệt
 * chú ý đến <b>idempotency</b> – tránh double-charge là yêu cầu bắt buộc.
 *
 * <p><b>Idempotency strategy:</b>
 * <ul>
 *   <li>DB level: UNIQUE constraint trên {@code booking_id} trong transactions table</li>
 *   <li>Application level: check trước khi gọi Stripe</li>
 *   <li>Stripe level: truyền {@code idempotencyKey = bookingId} vào mọi Stripe API call</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentDomainService {

    private final TransactionRepository transactionRepository;

    // ── Idempotency Checks ─────────────────────────────────────────

    /**
     * Kiểm tra booking đã có transaction chưa (idempotency check).
     *
     * <p>Nếu Kafka consumer nhận lại cùng 1 {@code booking.created} event
     * (retry/redelivery), phải skip để tránh double-charge.
     *
     * @param bookingId ID booking cần kiểm tra
     * @throws BusinessException nếu đã tồn tại transaction cho booking này
     */
    public void validateNoDuplicateTransaction(String bookingId) {
        transactionRepository.findByBookingId(bookingId).ifPresent(existing -> {
            if (existing.isSucceeded()) {
                throw new BusinessException(
                        "Payment already processed for booking: " + bookingId,
                        "DUPLICATE_PAYMENT", HttpStatus.CONFLICT);
            }
            if (existing.isProcessing() || existing.isPending()) {
                throw new BusinessException(
                        "Payment is already in progress for booking: " + bookingId,
                        "PAYMENT_IN_PROGRESS", HttpStatus.CONFLICT);
            }
            // FAILED/CANCELLED → cho phép retry
            log.info("[DOMAIN] Existing {} transaction found for booking {} – allowing retry",
                    existing.getStatus(), bookingId);
        });
    }

    /**
     * Lấy transaction SUCCEEDED để refund.
     *
     * @param bookingId ID booking cần refund
     * @return Transaction thành công
     * @throws ResourceNotFoundException nếu không tìm thấy
     * @throws BusinessException         nếu transaction chưa SUCCEEDED
     */
    public Transaction getSucceededTransactionForRefund(String bookingId) {
        Transaction transaction = transactionRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "bookingId", bookingId));

        if (!transaction.isSucceeded()) {
            throw new BusinessException(
                    "Transaction for booking " + bookingId + " is not in SUCCEEDED state: "
                            + transaction.getStatus(),
                    "INVALID_REFUND_STATE", HttpStatus.CONFLICT);
        }
        return transaction;
    }

    /**
     * Validate amount hợp lệ cho payment.
     */
    public void validateAmount(java.math.BigDecimal amount) {
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Payment amount must be greater than 0",
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST);
        }
        // Stripe giới hạn 999999.99 USD per charge
        if (amount.compareTo(java.math.BigDecimal.valueOf(999999.99)) > 0) {
            throw new BusinessException(
                    "Payment amount exceeds maximum allowed",
                    "AMOUNT_TOO_LARGE", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Lấy transaction theo bookingId.
     */
    public Transaction getByBookingId(String bookingId) {
        return transactionRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction", "bookingId", bookingId));
    }
}