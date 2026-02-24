package com.ticketmaster.notification.application.service;

import com.ticketmaster.notification.domain.model.Notification;
import com.ticketmaster.notification.domain.model.NotificationType;
import com.ticketmaster.notification.domain.repository.NotificationRepository;
import com.ticketmaster.notification.infrastructure.email.JavaMailSenderAdapter;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Application Service xử lý gửi email notification.
 *
 * <p>Render Thymeleaf HTML template → gửi qua {@link JavaMailSenderAdapter}.
 *
 * <p><b>Templates được hỗ trợ:</b>
 * <ul>
 *   <li>{@code booking-confirmed.html} – Xác nhận đặt vé thành công</li>
 *   <li>{@code payment-failed.html}    – Thông báo thanh toán thất bại</li>
 *   <li>Inline text fallback nếu template không render được</li>
 * </ul>
 *
 * <p><b>Async:</b> Gửi email trong thread pool riêng để không block Kafka consumer.
 * Kết quả (success/fail) được cập nhật vào DB.
 *
 * <p><b>Retry:</b> Tối đa 3 lần. {@code canRetryEmail()} check trước khi gửi lại.
 *
 * <p><b>Thymeleaf prefix:</b> Được cấu hình trong {@code application.yml}:
 * {@code classpath:/templates/email/} → chỉ cần truyền template name không cần path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSenderAdapter javaMailSenderAdapter;
    private final NotificationRepository notificationRepository;
    private final TemplateEngine         templateEngine;

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Gửi email xác nhận booking thành công.
     *
     * @param notification    Notification domain object đã lưu DB
     * @param bookingId       ID booking để hiển thị trong email
     * @param eventName       Tên sự kiện
     * @param totalAmount     Số tiền đã thanh toán
     * @param seatCount       Số ghế đã đặt
     * @param transactionId   Stripe transaction ID để hiển thị receipt
     */
    @Async("notificationTaskExecutor")
    public void sendBookingConfirmed(Notification notification, String bookingId,
                                      String eventName, BigDecimal totalAmount,
                                      int seatCount, String transactionId) {
        log.info("[EMAIL] Sending booking-confirmed to {} bookingId={}",
                notification.getRecipientEmail(), bookingId);

        Context ctx = new Context();
        ctx.setVariables(Map.of(
                "recipientName",   extractName(notification.getRecipientEmail()),
                "bookingId",       bookingId,
                "eventName",       eventName,
                "totalAmount",     totalAmount,
                "seatCount",       seatCount,
                "transactionId",   transactionId,
                "supportEmail",    "support@ticketmaster.com",
                "year",            java.time.Year.now().getValue()
        ));

        sendWithTemplate(notification, "booking-confirmed",
                "🎟 Booking Confirmed – " + eventName);
    }

    /**
     * Gửi email thông báo thanh toán thất bại.
     *
     * @param notification    Notification domain object đã lưu DB
     * @param bookingId       ID booking
     * @param eventName       Tên sự kiện
     * @param failureReason   Lý do thất bại (human-readable từ Stripe)
     * @param amount          Số tiền cố gắng thanh toán
     */
    @Async("notificationTaskExecutor")
    public void sendPaymentFailed(Notification notification, String bookingId,
                                   String eventName, String failureReason,
                                   BigDecimal amount) {
        log.info("[EMAIL] Sending payment-failed to {} bookingId={}",
                notification.getRecipientEmail(), bookingId);

        Context ctx = new Context();
        ctx.setVariables(Map.of(
                "recipientName",  extractName(notification.getRecipientEmail()),
                "bookingId",      bookingId,
                "eventName",      eventName,
                "failureReason",  failureReason,
                "amount",         amount,
                "supportEmail",   "support@ticketmaster.com",
                "year",           java.time.Year.now().getValue()
        ));

        sendWithTemplate(notification, "payment-failed",
                "⚠️ Payment Failed – Action Required");
    }

    /**
     * Gửi email generic với subject và message đơn giản (không dùng template).
     * Dùng cho BOOKING_CANCELLED, BOOKING_EXPIRED, SYSTEM_ANNOUNCEMENT.
     */
    @Async("notificationTaskExecutor")
    public void sendSimpleEmail(Notification notification) {
        log.info("[EMAIL] Sending simple email type={} to {}",
                notification.getType(), notification.getRecipientEmail());
        try {
            javaMailSenderAdapter.sendSimpleText(
                    notification.getRecipientEmail(),
                    notification.getTitle(),
                    notification.getMessage()
            );
            markSent(notification);
        } catch (Exception e) {
            markFailed(notification, e.getMessage());
        }
    }

    // ── Private Helpers ───────────────────────────────────────────

    /**
     * Render Thymeleaf template thành HTML rồi gửi email.
     *
     * @param notification    Notification cần gửi
     * @param templateName    Tên template (không có extension, vd: "booking-confirmed")
     * @param subject         Email subject
     */
    private void sendWithTemplate(Notification notification,
                                   String templateName, String subject) {
        try {
            // Render Thymeleaf template (prefix + templateName + suffix = classpath:/templates/email/booking-confirmed.html)
            Context ctx = buildBaseContext(notification);
            String htmlContent = templateEngine.process(templateName, ctx);

            javaMailSenderAdapter.sendHtml(
                    notification.getRecipientEmail(),
                    subject,
                    htmlContent
            );

            markSent(notification);
            log.info("[EMAIL] ✅ Email sent | type={} to={} template={}",
                    notification.getType(), notification.getRecipientEmail(), templateName);

        } catch (MessagingException e) {
            log.error("[EMAIL] ❌ Failed to send email | type={} to={} error={}",
                    notification.getType(), notification.getRecipientEmail(), e.getMessage(), e);
            markFailed(notification, e.getMessage());
        } catch (Exception e) {
            log.error("[EMAIL] Unexpected error sending email | type={} to={}",
                    notification.getType(), notification.getRecipientEmail(), e);
            markFailed(notification, "Unexpected error: " + e.getMessage());
        }
    }

    private Context buildBaseContext(Notification notification) {
        Context ctx = new Context();
        ctx.setVariable("notification", notification);
        ctx.setVariable("appName", "Ticketmaster");
        ctx.setVariable("year", java.time.Year.now().getValue());
        ctx.setVariable("supportEmail", "support@ticketmaster.com");
        return ctx;
    }

    private void markSent(Notification notification) {
        notificationRepository.save(notification.markEmailSent());
    }

    private void markFailed(Notification notification, String reason) {
        notificationRepository.save(notification.markEmailFailed(reason));
    }

    /** Lấy tên từ email: "john.doe@gmail.com" → "John" */
    private String extractName(String email) {
        if (email == null) return "Customer";
        String localPart = email.split("@")[0];
        String firstName = localPart.split("[._-]")[0];
        return Character.toUpperCase(firstName.charAt(0)) + firstName.substring(1);
    }
}