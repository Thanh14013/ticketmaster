package com.ticketmaster.payment.infrastructure.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe SDK configuration.
 *
 * <p>Khởi tạo Stripe API key khi service start.
 * Stripe SDK dùng static field nên chỉ cần set một lần.
 *
 * <p>Key được inject từ environment variable {@code STRIPE_SECRET_KEY}
 * → không bao giờ hardcode trong source code.
 *
 * <p><b>Sandbox vs Production:</b>
 * <ul>
 *   <li>Test: {@code sk_test_...} → không charge tiền thật</li>
 *   <li>Live: {@code sk_live_...} → charge tiền thật</li>
 * </ul>
 */
@Slf4j
@Configuration
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.api-version:2024-04-10}")
    private String apiVersion;

    @PostConstruct
    public void initStripe() {
        Stripe.apiKey = secretKey;
        log.info("[STRIPE] SDK initialized | apiVersion={} | keyPrefix={}",
                apiVersion,
                secretKey.length() > 10 ? secretKey.substring(0, 10) + "..." : "***");
    }
}

