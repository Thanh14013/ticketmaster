package com.ticketmaster.notification.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 documentation cho notification-service.
 * Swagger UI: <a href="http://localhost:8085/swagger-ui.html">http://localhost:8085/swagger-ui.html</a>
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketmaster – Notification Service API")
                        .description("""
                            Notification Service: Email + SSE real-time notifications.
                            
                            **Kafka Consumers:**
                            - `booking.created`   → pending payment reminder
                            - `payment.processed` → booking confirmed email (Thymeleaf template)
                            - `payment.failed`    → payment failed email + retry instructions
                            
                            **SSE Endpoint:**
                            `GET /api/v1/notifications/stream` – Subscribe real-time notifications
                            """)
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}