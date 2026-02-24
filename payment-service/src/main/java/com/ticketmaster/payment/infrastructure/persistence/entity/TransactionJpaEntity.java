package com.ticketmaster.payment.infrastructure.persistence.entity;

import com.ticketmaster.payment.domain.model.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity cho bảng {@code transactions}.
 *
 * <p>{@link com.ticketmaster.payment.domain.model.PaymentMethod} Value Object
 * được embed dưới dạng các cột riêng (pm_*) thay vì tạo bảng riêng.
 *
 * <p>Index design:
 * <ul>
 *   <li>UNIQUE {@code booking_id} – idempotency constraint</li>
 *   <li>{@code (status, created_at)} – tìm PROCESSING transactions để reconcile</li>
 *   <li>{@code user_id} – lấy payment history của user</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_booking_id", columnList = "booking_id", unique = true),
        @Index(name = "idx_transactions_user_id",    columnList = "user_id"),
        @Index(name = "idx_transactions_status",     columnList = "status, created_at")
})
public class TransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    /** UNIQUE – idempotency key. Mỗi booking chỉ có 1 transaction record. */
    @Column(name = "booking_id", nullable = false, unique = true, length = 36)
    private String bookingId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    // ── PaymentMethod (embedded Value Object) ─────────────────────

    @Column(name = "pm_stripe_id", length = 100)
    private String pmStripeId;

    @Column(name = "pm_type", length = 20)
    private String pmType;

    @Column(name = "pm_card_brand", length = 20)
    private String pmCardBrand;

    @Column(name = "pm_card_last4", length = 4)
    private String pmCardLast4;

    @Column(name = "pm_exp_month")
    private Integer pmExpMonth;

    @Column(name = "pm_exp_year")
    private Integer pmExpYear;

    // ── Stripe References ──────────────────────────────────────────

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id", length = 100)
    private String stripeChargeId;

    @Column(name = "stripe_refund_id", length = 100)
    private String stripeRefundId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}