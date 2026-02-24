package com.ticketmaster.payment.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 documentation cho payment-service.
 * Swagger UI: <a href="http://localhost:8084/swagger-ui.html">http://localhost:8084/swagger-ui.html</a>
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketmaster – Payment Service API")
                        .description("""
                            Payment Processing via Stripe.
                            
                            **Architecture:** Hexagonal (Ports & Adapters)
                            - Port: `PaymentGatewayPort` (interface)
                            - Adapter: `StripePaymentAdapter` (Stripe SDK)
                            
                            **Resilience:**
                            - CircuitBreaker: opens after 50% failures in 10 calls
                            - Retry: 3 attempts, 2s wait (IOException/SocketTimeoutException only)
                            
                            **Flow:**
                            1. Consume `booking.created` from Kafka
                            2. Charge via Stripe (idempotency key = bookingId)
                            3. Publish `payment.processed` or `payment.failed`
                            """)
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}