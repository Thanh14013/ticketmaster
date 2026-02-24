package com.ticketmaster.notification.application.kafka;

import com.ticketmaster.common.event.BookingCreatedEvent;
import com.ticketmaster.common.event.PaymentFailedEvent;
import com.ticketmaster.common.event.PaymentProcessedEvent;
import com.ticketmaster.common.util.IdGenerator;
import com.ticketmaster.notification.application.service.EmailNotificationService;
import com.ticketmaster.notification.application.service.SseNotificationService;
import com.ticketmaster.notification.domain.model.Notification;
import com.ticketmaster.notification.domain.model.NotificationType;
import com.ticketmaster.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer trung tâm của notification-service.
 *
 * <p>Consume TẤT CẢ events cần gửi notification:
 * <ul>
 *   <li>{@code booking.created}   → "Booking pending – please pay within 2 minutes"</li>
 *   <li>{@code payment.processed} → "Booking confirmed 🎟 + e-ticket"</li>
 *   <li>{@code payment.failed}    → "Payment failed – please retry"</li>
 * </ul>
 *
 * <p><b>Flow mỗi event:</b>
 * <ol>
 *   <li>Idempotency check – tránh gửi notification 2 lần cho cùng event</li>
 *   <li>Tạo và lưu {@link Notification} aggregate vào DB</li>
 *   <li>Push SSE real-time (async)</li>
 *   <li>Gửi email (async)</li>
 *   <li>ACK Kafka message sau khi DB save thành công</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> Check {@code existsByReferenceIdAndType} trước khi xử lý.
 * Email/SSE gửi async → nếu chúng fail thì DB đã lưu, có thể retry sau.
 *
 * <p><b>Manual ACK:</b> Chỉ ACK sau khi lưu DB thành công.
 * Nếu email/SSE fail → đã có retry mechanism riêng, không cần re-consume Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationRepository  notificationRepository;
    private final EmailNotificationService emailNotificationService;
    private final SseNotificationService   sseNotificationService;

    // ── booking.created ───────────────────────────────────────────

    @KafkaListener(
        topics           = "booking.created",
        groupId          = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBookingCreated(
            @Payload BookingCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] booking.created | bookingId={} userId={} | partition={} offset={}",
                event.getBookingId(), event.getUserId(), partition, offset);

        // Idempotency check
        if (notificationRepository.existsByReferenceIdAndType(
                event.getBookingId(), NotificationType.BOOKING_CREATED)) {
            log.warn("[KAFKA] Duplicate booking.created for bookingId={} – skipping",
                    event.getBookingId());
            acknowledgment.acknowledge();
            return;
        }

        try {
            String title   = "Booking Pending – Complete Payment";
            String message = String.format(
                    "Your booking for '%s' is pending. Please complete payment within 2 minutes "
                    + "or your seats will be released. Booking ID: %s",
                    event.getEventName(), event.getBookingId());

            Notification notification = Notification.create(
                    IdGenerator.newId(),
                    event.getUserId(),
                    event.getUserEmail(),
                    NotificationType.BOOKING_CREATED,
                    title,
                    message,
                    event.getBookingId(),
                    "BOOKING"
            );
            Notification saved = notificationRepository.save(notification);

            // Push SSE (async)
            sseNotificationService.push(saved);

            // Gửi email đơn giản (không có template riêng cho BOOKING_CREATED)
            emailNotificationService.sendSimpleEmail(saved);

            acknowledgment.acknowledge();
            log.info("[KAFKA] booking.created notification created | id={}", saved.getId());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to handle booking.created | bookingId={}: {}",
                    event.getBookingId(), ex.getMessage(), ex);
            throw ex; // Không ACK → retry → DLQ
        }
    }

    // ── payment.processed ─────────────────────────────────────────

    @KafkaListener(
        topics           = "payment.processed",
        groupId          = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentProcessed(
            @Payload PaymentProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] payment.processed | bookingId={} amount={} | partition={} offset={}",
                event.getBookingId(), event.getAmount(), partition, offset);

        // Idempotency check
        if (notificationRepository.existsByReferenceIdAndType(
                event.getBookingId(), NotificationType.PAYMENT_PROCESSED)) {
            log.warn("[KAFKA] Duplicate payment.processed for bookingId={} – skipping",
                    event.getBookingId());
            acknowledgment.acknowledge();
            return;
        }

        try {
            String title   = "🎟 Booking Confirmed!";
            String message = String.format(
                    "Your booking has been confirmed! Amount: $%s. Transaction ID: %s",
                    event.getAmount(), event.getTransactionId());

            Notification notification = Notification.create(
                    IdGenerator.newId(),
                    event.getUserId(),
                    event.getUserId() + "@placeholder.com", // lấy email từ event nếu có
                    NotificationType.PAYMENT_PROCESSED,
                    title,
                    message,
                    event.getBookingId(),
                    "BOOKING"
            );
            Notification saved = notificationRepository.save(notification);

            // Push SSE
            sseNotificationService.push(saved);

            // Gửi email "booking-confirmed" với Thymeleaf template
            emailNotificationService.sendBookingConfirmed(
                    saved,
                    event.getBookingId(),
                    "Your Event",       // eventName – lấy từ event nếu có
                    event.getAmount(),
                    1,                  // seatCount – lấy từ event nếu có
                    event.getTransactionId()
            );

            acknowledgment.acknowledge();
            log.info("[KAFKA] payment.processed notification sent | id={}", saved.getId());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to handle payment.processed | bookingId={}: {}",
                    event.getBookingId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ── payment.failed ────────────────────────────────────────────

    @KafkaListener(
        topics           = "payment.failed",
        groupId          = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset,
            Acknowledgment acknowledgment) {

        log.info("[KAFKA] payment.failed | bookingId={} code={} | partition={} offset={}",
                event.getBookingId(), event.getFailureCode(), partition, offset);

        // Idempotency check
        if (notificationRepository.existsByReferenceIdAndType(
                event.getBookingId(), NotificationType.PAYMENT_FAILED)) {
            log.warn("[KAFKA] Duplicate payment.failed for bookingId={} – skipping",
                    event.getBookingId());
            acknowledgment.acknowledge();
            return;
        }

        try {
            String title   = "⚠️ Payment Failed";
            String message = String.format(
                    "Payment failed for your booking. Reason: %s. "
                    + "Your seats have been released. Booking ID: %s",
                    event.getFailureMessage(), event.getBookingId());

            Notification notification = Notification.create(
                    IdGenerator.newId(),
                    event.getUserId(),
                    event.getUserEmail(),
                    NotificationType.PAYMENT_FAILED,
                    title,
                    message,
                    event.getBookingId(),
                    "BOOKING"
            );
            Notification saved = notificationRepository.save(notification);

            // Push SSE
            sseNotificationService.push(saved);

            // Gửi email "payment-failed" với Thymeleaf template
            emailNotificationService.sendPaymentFailed(
                    saved,
                    event.getBookingId(),
                    "Your Event",
                    event.getFailureMessage(),
                    event.getAttemptedAmount()
            );

            acknowledgment.acknowledge();
            log.info("[KAFKA] payment.failed notification sent | id={}", saved.getId());

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to handle payment.failed | bookingId={}: {}",
                    event.getBookingId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}