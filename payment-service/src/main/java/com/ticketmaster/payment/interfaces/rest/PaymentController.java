package com.ticketmaster.payment.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.payment.application.command.ProcessPaymentCommand;
import com.ticketmaster.payment.application.handler.ProcessPaymentHandler;
import com.ticketmaster.payment.application.port.PaymentGatewayPort;
import com.ticketmaster.payment.domain.model.Transaction;
import com.ticketmaster.payment.domain.repository.TransactionRepository;
import com.ticketmaster.payment.domain.service.PaymentDomainService;
import com.ticketmaster.payment.infrastructure.persistence.mapper.TransactionMapper;
import com.ticketmaster.payment.interfaces.dto.PaymentRequest;
import com.ticketmaster.payment.interfaces.dto.PaymentResponse;
import com.ticketmaster.payment.interfaces.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller cho Payment Service API.
 *
 * <p><b>Base URL:</b> {@code /api/v1/payments}
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code POST   /}               – Trigger manual payment (testing)</li>
 *   <li>{@code GET    /{transactionId}} – Lấy chi tiết transaction</li>
 *   <li>{@code GET    /booking/{bookingId}} – Lấy transaction theo bookingId</li>
 *   <li>{@code GET    /user/{userId}}   – Lấy lịch sử payment của user</li>
 *   <li>{@code POST   /{transactionId}/refund} – Thực hiện refund</li>
 * </ul>
 *
 * <p><b>Authentication:</b> JWT Bearer token – validated bởi api-gateway trước khi
 * request đến đây. Payment service không validate JWT lần 2.
 *
 * <p><b>Note về manual payment:</b> Trong production, payment được trigger tự động
 * bởi Kafka consumer. REST endpoint này phục vụ manual retry và testing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment processing and transaction management APIs")
public class PaymentController {

    private final ProcessPaymentHandler  processPaymentHandler;
    private final PaymentDomainService   paymentDomainService;
    private final TransactionRepository  transactionRepository;
    private final PaymentGatewayPort     paymentGatewayPort;
    private final TransactionMapper      transactionMapper;

    // ── POST /api/v1/payments ────────────────────────────────────

    /**
     * Trigger manual payment.
     *
     * <p>Chủ yếu dùng cho testing và manual retry.
     * Production flow sử dụng Kafka consumer.
     */
    @PostMapping
    @Operation(
            summary     = "Process payment manually",
            description = "Trigger payment processing for a booking. Primarily for testing/retry.")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request) {

        log.info("[API] POST /api/v1/payments | bookingId={} amount={}",
                request.getBookingId(), request.getAmount());

        ProcessPaymentCommand command = ProcessPaymentCommand.builder()
                .bookingId(request.getBookingId())
                .paymentMethodId(request.getPaymentMethodId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();

        TransactionResponse txnResponse = processPaymentHandler.handle(command);

        if (txnResponse == null) {
            return ResponseEntity.ok(
                    ApiResponse.ok("Booking already processed or expired", null));
        }

        PaymentResponse paymentResponse = PaymentResponse.from(txnResponse);
        return ResponseEntity.ok(ApiResponse.ok("Payment processed", paymentResponse));
    }

    // ── GET /api/v1/payments/{transactionId} ─────────────────────

    /**
     * Lấy chi tiết một transaction theo ID.
     */
    @GetMapping("/{transactionId}")
    @Operation(
            summary     = "Get transaction by ID",
            description = "Retrieve full transaction details including Stripe references.")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        log.debug("[API] GET /api/v1/payments/{}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new com.ticketmaster.common.exception.ResourceNotFoundException(
                        "Transaction", "id", transactionId));

        return ResponseEntity.ok(ApiResponse.ok(transactionMapper.toResponse(transaction)));
    }

    // ── GET /api/v1/payments/booking/{bookingId} ─────────────────

    /**
     * Lấy transaction theo bookingId.
     * Dùng bởi booking-service hoặc frontend để check payment status.
     */
    @GetMapping("/booking/{bookingId}")
    @Operation(
            summary     = "Get transaction by booking ID",
            description = "Lookup payment transaction associated with a specific booking.")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByBookingId(
            @Parameter(description = "Booking ID") @PathVariable String bookingId) {

        log.debug("[API] GET /api/v1/payments/booking/{}", bookingId);

        Transaction transaction = paymentDomainService.getByBookingId(bookingId);
        return ResponseEntity.ok(ApiResponse.ok(transactionMapper.toResponse(transaction)));
    }

    // ── GET /api/v1/payments/user/{userId} ───────────────────────

    /**
     * Lấy lịch sử thanh toán của một user.
     */
    @GetMapping("/user/{userId}")
    @Operation(
            summary     = "Get payment history for a user",
            description = "Returns all transactions ordered by date descending.")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getUserPayments(
            @Parameter(description = "User ID") @PathVariable String userId) {

        log.debug("[API] GET /api/v1/payments/user/{}", userId);

        List<TransactionResponse> responses = transactionRepository
                .findByUserId(userId)
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    // ── POST /api/v1/payments/{transactionId}/refund ─────────────

    /**
     * Thực hiện refund cho một transaction đã SUCCEEDED.
     *
     * <p>Dùng khi user huỷ booking sau khi đã thanh toán thành công.
     * Trong production, được trigger bởi booking-service khi nhận cancel request.
     *
     * @param transactionId Transaction ID cần refund
     * @param bookingId     BookingId để idempotency (optional, dùng transactionId nếu null)
     */
    @PostMapping("/{transactionId}/refund")
    @Operation(
            summary     = "Refund a successful transaction",
            description = "Issue a full refund for a SUCCEEDED transaction. " +
                          "Partial refund not supported in current version.")
    public ResponseEntity<ApiResponse<TransactionResponse>> refundTransaction(
            @Parameter(description = "Transaction ID to refund")
            @PathVariable String transactionId,

            @Parameter(description = "Booking ID for idempotency key (optional)")
            @RequestParam(required = false) String bookingId) {

        log.info("[API] POST /api/v1/payments/{}/refund | bookingId={}", transactionId, bookingId);

        // 1. Load transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new com.ticketmaster.common.exception.ResourceNotFoundException(
                        "Transaction", "id", transactionId));

        // 2. Validate có thể refund
        if (!transaction.isSucceeded()) {
            throw new com.ticketmaster.common.exception.BusinessException(
                    "Can only refund SUCCEEDED transactions. Current status: " + transaction.getStatus(),
                    "INVALID_REFUND_STATE",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        // 3. Gọi Stripe refund
        String idempotencyKey = bookingId != null ? bookingId : transactionId;
        PaymentGatewayPort.RefundResult refundResult = paymentGatewayPort.refund(
                transaction.getStripeChargeId(), null, idempotencyKey);

        // 4. Update transaction domain
        Transaction refunded;
        if (refundResult.succeeded()) {
            refunded = transaction.refund(refundResult.refundId());
        } else {
            throw new com.ticketmaster.common.exception.BusinessException(
                    "Refund failed: " + refundResult.failureMessage(),
                    "REFUND_FAILED",
                    org.springframework.http.HttpStatus.BAD_GATEWAY);
        }

        Transaction saved = transactionRepository.save(refunded);
        log.info("[API] Refund succeeded | transactionId={} refundId={}",
                transactionId, refundResult.refundId());

        return ResponseEntity.ok(
                ApiResponse.ok("Refund processed successfully", transactionMapper.toResponse(saved)));
    }
}

