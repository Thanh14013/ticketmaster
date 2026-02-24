package com.ticketmaster.payment.application.kafka;

import com.ticketmaster.common.event.BookingCreatedEvent;
import com.ticketmaster.payment.application.command.ProcessPaymentCommand;
import com.ticketmaster.payment.application.handler.ProcessPaymentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer lắng nghe topic {@code booking.created}.
 *
 * <p>Được publish bởi {@code booking-service} khi user tạo booking thành công.
 * Payment-service nhận event này để bắt đầu xử lý thanh toán.
 *
 * <p><b>Luồng xử lý:</b>
 * <ol>
 *   <li>Nhận {@link BookingCreatedEvent} từ topic {@code booking.created}</li>
 *   <li>Map sang {@link ProcessPaymentCommand}</li>
 *   <li>Delegate xuống {@link ProcessPaymentHandler}</li>
 *   <li>ACK chỉ sau khi xử lý thành công (Manual Acknowledgment)</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> {@code ProcessPaymentHandler} kiểm tra duplicate trước
 * khi gọi Stripe – nên Kafka retry an toàn.
 *
 * <p><b>Note về paymentMethodId:</b>
 * Trong production flow, user nhập thẻ ở frontend → Stripe.js trả về PM ID →
 * PM ID được gửi kèm trong request tạo booking → booking-service forward vào event.
 * Nếu event không có PM ID → dùng default test card trong sandbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final ProcessPaymentHandler processPaymentHandler;

    @KafkaListener(
            topics           = "booking.created",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBookingCreated(
            @Payload BookingCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] booking.created | bookingId={} userId={} amount={} | partition={} offset={}",
                event.getBookingId(), event.getUserId(), event.getTotalAmount(),
                partition, offset);

        try {
            ProcessPaymentCommand command = ProcessPaymentCommand.builder()
                    .bookingId(event.getBookingId())
                    .userId(event.getUserId())
                    .userEmail(event.getUserEmail())
                    .amount(event.getTotalAmount())
                    .currency(event.getCurrency() != null ? event.getCurrency() : "USD")
                    .paymentMethodId(event.getPaymentMethodId() != null
                            ? event.getPaymentMethodId()
                            : "pm_card_visa")  // Stripe test card fallback
                    .eventName(event.getEventName())
                    .seatIds(event.getSeatIds())
                    .bookingExpiresAt(event.getExpiresAt())
                    .kafkaEventId(event.getEventId())
                    .build();

            processPaymentHandler.handle(command);

            acknowledgment.acknowledge();
            log.info("[KAFKA] booking.created processed | bookingId={}", event.getBookingId());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to process booking.created | bookingId={} error={}",
                    event.getBookingId(), ex.getMessage(), ex);
            // Không ACK → retry → DLQ sau max retries
            throw ex;
        }
    }
}