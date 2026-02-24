package com.ticketmaster.notification.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adapter wrapping Spring {@link JavaMailSender} để gửi email.
 *
 * <p>Cung cấp 2 mode:
 * <ul>
 *   <li>{@link #sendHtml} – Gửi email HTML (dùng Thymeleaf template)</li>
 *   <li>{@link #sendSimpleText} – Gửi email text đơn giản (fallback)</li>
 * </ul>
 *
 * <p><b>Adapter pattern:</b> Domain/Application layer gọi service này
 * thay vì trực tiếp gọi JavaMailSender → dễ mock trong test, dễ swap mail provider.
 *
 * <p><b>Sender config:</b> {@code spring.mail.*} trong application.yml.
 * SMTP auth + STARTTLS được bật mặc định.
 *
 * <p><b>Gmail setup:</b>
 * <ul>
 *   <li>Enable 2FA trên Gmail</li>
 *   <li>Generate App Password: Google Account → Security → App Passwords</li>
 *   <li>Dùng App Password trong {@code MAIL_PASSWORD} env var</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JavaMailSenderAdapter {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${spring.application.name:Ticketmaster}")
    private String senderName;

    /**
     * Gửi email HTML (multipart, dùng MimeMessage).
     *
     * @param to          địa chỉ email người nhận
     * @param subject     tiêu đề email
     * @param htmlContent nội dung HTML đã render bởi Thymeleaf
     * @throws MessagingException nếu không tạo được MimeMessage
     */
    public void sendHtml(String to, String subject, String htmlContent)
            throws MessagingException {
        if (!isValidEmail(to)) {
            log.warn("[MAIL] Invalid email address: {} – skipping", to);
            return;
        }

        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail, senderName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true = isHtml

        javaMailSender.send(message);
        log.debug("[MAIL] HTML email sent | to={} subject={}", to, subject);
    }

    /**
     * Gửi email text thuần (SimpleMailMessage – không cần MimeMessage).
     * Dùng cho notifications đơn giản không cần HTML.
     *
     * @param to      địa chỉ email người nhận
     * @param subject tiêu đề email
     * @param text    nội dung text thuần
     */
    public void sendSimpleText(String to, String subject, String text) {
        if (!isValidEmail(to)) {
            log.warn("[MAIL] Invalid email address: {} – skipping", to);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        javaMailSender.send(message);
        log.debug("[MAIL] Simple email sent | to={} subject={}", to, subject);
    }

    /**
     * Basic email validation (tránh gọi SMTP với invalid address).
     */
    private boolean isValidEmail(String email) {
        return email != null
                && email.contains("@")
                && email.contains(".")
                && !email.endsWith("@placeholder.com"); // Skip placeholder emails
    }
}