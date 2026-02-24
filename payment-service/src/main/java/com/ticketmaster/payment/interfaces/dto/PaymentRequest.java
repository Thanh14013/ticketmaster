package com.ticketmaster.payment.interfaces.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO cho manual payment initiation via REST API.
 *
 * <p>Endpoint: {@code POST /api/v1/payments}
 *
 * <p><b>Note:</b> Trong production flow, payments chủ yếu được trigger tự động
 * bởi Kafka consumer ({@code BookingEventConsumer}) khi nhận {@code booking.created}.
 * REST endpoint này dùng cho retry thủ công hoặc testing.
 */
@Getter
@NoArgsConstructor
public class PaymentRequest {

    /**
     * ID của booking cần thanh toán.
     * Phải tồn tại trong booking-service.
     */
    @NotBlank(message = "bookingId is required")
    @Size(max = 36, message = "bookingId must not exceed 36 characters")
    private String bookingId;

    /**
     * Stripe Payment Method ID (vd: {@code pm_card_visa}).
     * Được lấy từ Stripe.js trên frontend sau khi user nhập thẻ.
     */
    @NotBlank(message = "paymentMethodId is required")
    private String paymentMethodId;

    /**
     * Số tiền cần thanh toán.
     * Phải lớn hơn 0 và khớp với total_amount của booking.
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    /**
     * Đơn vị tiền tệ theo ISO 4217 (vd: "USD", "VND").
     * Default là "USD" nếu không cung cấp.
     */
    @Size(min = 3, max = 3, message = "currency must be exactly 3 characters (ISO 4217)")
    private String currency = "USD";
}

