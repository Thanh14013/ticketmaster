package com.ticketmaster.payment.application.kafka;

import com.ticketmaster.common.event.PaymentFailedEvent;
import com.ticketmaster.common.event.PaymentProcessedEvent;
import com.ticketmaster.payment.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer cho payment-service.
 *
 * <p><b>Topics được publish:</b>
 * <ul>
 *   <li>{@code payment.processed} – payment thành công</li>
 *   <li>{@code payment.failed}    – payment thất bại</li>
 * </ul>
 *
 * <p><b>Consumers:</b>
 * <ul>
 *   <li>{@code booking-service}:      CONFIRM | CANCEL booking</li>
 *   <li>{@code notification-service}: gửi email xác nhận | thông báo lỗi</li>
 * </ul>
 *
 * <p><b>Kafka message key = bookingId:</b> Đảm bảo tất cả events của cùng 1 booking
 * được xử lý theo đúng thứ tự (cùng partition).
 *
 * <p><b>Durability:</b> {@code acks=all} + {@code enable.idempotence=true}
 * – đảm bảo payment events không bị mất.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private static final String TOPIC_PAYMENT_PROCESSED = "payment.processed";
    private static final String TOPIC_PAYMENT_FAILED    = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish {@code payment.processed} – thanh toán thành công.
     *
     * @param transaction Transaction domain object đã SUCCEEDED
     */
    public void publishPaymentProcessed(Transaction transaction) {
        PaymentProcessedEvent event = PaymentProcessedEvent.of(
                transaction.getBookingId(),
                transaction.getUserId(),
                transaction.getStripeChargeId(),
                transaction.getId(),
                transaction.getAmount()
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_PAYMENT_PROCESSED, transaction.getBookingId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA] Failed to publish payment.processed | bookingId={}: {}",
                        transaction.getBookingId(), ex.getMessage(), ex);
                // TODO: Outbox pattern để đảm bảo delivery
            } else {
                log.info("[KAFKA] payment.processed published | bookingId={} partition={} offset={}",
                        transaction.getBookingId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish {@code payment.failed} – thanh toán thất bại.
     *
     * @param transaction    Transaction domain object đã FAILED
     * @param userEmail      email user để notification-service gửi mail
     * @param failureCode    mã lỗi Stripe
     * @param failureMessage mô tả lỗi
     */
    public void publishPaymentFailed(Transaction transaction, String userEmail,
                                     String failureCode, String failureMessage) {
        PaymentFailedEvent event = PaymentFailedEvent.of(
                transaction.getBookingId(),
                transaction.getUserId(),
                userEmail != null ? userEmail : transaction.getUserEmail(),
                transaction.getAmount(),
                failureCode,
                failureMessage
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_PAYMENT_FAILED, transaction.getBookingId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA] Failed to publish payment.failed | bookingId={}: {}",
                        transaction.getBookingId(), ex.getMessage(), ex);
            } else {
                log.warn("[KAFKA] payment.failed published | bookingId={} failureCode={} partition={} offset={}",
                        transaction.getBookingId(), failureCode,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}