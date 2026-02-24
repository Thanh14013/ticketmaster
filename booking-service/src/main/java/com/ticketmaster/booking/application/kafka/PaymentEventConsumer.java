package com.ticketmaster.booking.application.kafka;

import com.ticketmaster.booking.application.command.CancelBookingCommand;
import com.ticketmaster.booking.application.command.ConfirmBookingCommand;
import com.ticketmaster.booking.application.handler.CancelBookingHandler;
import com.ticketmaster.booking.application.handler.ConfirmBookingHandler;
import com.ticketmaster.common.event.PaymentFailedEvent;
import com.ticketmaster.common.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka Consumer lắng nghe các payment events từ payment-service.
 *
 * <p><b>Topics được consume:</b>
 * <ul>
 *   <li>{@code payment.processed} – payment thành công → CONFIRM booking</li>
 *   <li>{@code payment.failed}    – payment thất bại → CANCEL booking + release seats</li>
 * </ul>
 *
 * <p><b>Manual Acknowledgment:</b> Chỉ ACK sau khi DB đã cập nhật thành công.
 * Nếu xử lý thất bại → không ACK → Kafka retry → DLQ sau max retries.
 *
 * <p><b>Idempotency:</b> ConfirmBookingHandler và CancelBookingHandler
 * đều handle trường hợp booking đã ở trạng thái đích (Kafka retry safe).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ConfirmBookingHandler confirmBookingHandler;
    private final CancelBookingHandler  cancelBookingHandler;

    /**
     * Consume {@code payment.processed} – thanh toán thành công.
     * Chuyển booking từ PENDING_PAYMENT → CONFIRMED.
     */
    @KafkaListener(
        topics           = "payment.processed",
        groupId          = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentProcessed(
            @Payload PaymentProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] payment.processed | bookingId={} transactionId={} | partition={} offset={}",
                event.getBookingId(), event.getTransactionId(), partition, offset);

        try {
            ConfirmBookingCommand command = ConfirmBookingCommand.builder()
                    .bookingId(event.getBookingId())
                    .transactionId(event.getTransactionId())
                    .userId(event.getUserId())
                    .kafkaEventId(event.getEventId())
                    .build();

            confirmBookingHandler.handle(command);
            acknowledgment.acknowledge();

            log.info("[KAFKA] payment.processed handled successfully | bookingId={}",
                    event.getBookingId());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to handle payment.processed | bookingId={} error={}",
                    event.getBookingId(), ex.getMessage(), ex);
            throw ex; // Không ACK → retry → DLQ
        }
    }

    /**
     * Consume {@code payment.failed} – thanh toán thất bại.
     * Chuyển booking từ PENDING_PAYMENT → CANCELLED và release seats.
     */
    @KafkaListener(
        topics           = "payment.failed",
        groupId          = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] payment.failed | bookingId={} reason={} | partition={} offset={}",
                event.getBookingId(), event.getFailureReason(), partition, offset);

        try {
            CancelBookingCommand command = CancelBookingCommand.builder()
                    .bookingId(event.getBookingId())
                    .userId(null)
                    .reason("Payment failed: " + event.getFailureReason())
                    .systemInitiated(true)
                    .build();

            cancelBookingHandler.handle(command);
            acknowledgment.acknowledge();

            log.info("[KAFKA] payment.failed handled successfully | bookingId={}",
                    event.getBookingId());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to handle payment.failed | bookingId={} error={}",
                    event.getBookingId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}