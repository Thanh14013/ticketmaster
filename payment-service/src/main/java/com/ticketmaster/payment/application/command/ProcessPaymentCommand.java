package com.ticketmaster.payment.application.command;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Command object cho use case xử lý thanh toán.
 *
 * <p>Được tạo từ {@code BookingCreatedEvent} Kafka message
 * trong {@link com.ticketmaster.payment.application.kafka.BookingEventConsumer}.
 */
@Getter
@Builder
public class ProcessPaymentCommand {

    private final String      bookingId;
    private final String      userId;
    private final String      userEmail;
    private final BigDecimal  amount;
    private final String      currency;

    /**
     * Stripe Payment Method ID do client cung cấp khi booking.
     * Trong flow thực tế, user nhập thẻ → frontend gọi Stripe.js → nhận PM ID
     * → gửi kèm khi POST /bookings.
     *
     * <p>Với demo flow, dùng test PM ID từ Stripe sandbox.
     */
    private final String      paymentMethodId;

    /** Tên event để hiển thị trong invoice/email. */
    private final String      eventName;

    /** Danh sách seatIds để ghi vào metadata của Stripe PaymentIntent. */
    private final List<String> seatIds;

    /** Thời điểm booking hết hạn – dùng để kiểm tra còn valid không. */
    private final Instant     bookingExpiresAt;

    /** Kafka event ID để đảm bảo idempotency. */
    private final String      kafkaEventId;
}