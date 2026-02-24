package com.ticketmaster.booking.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 documentation cho booking-service.
 * Swagger UI: <a href="http://localhost:8083/swagger-ui.html">http://localhost:8083/swagger-ui.html</a>
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI bookingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketmaster – Booking Service API")
                        .description("""
                            Core Booking Service: đặt vé, seat locking (Redisson), payment confirmation.
                            
                            **Flow:**
                            1. POST /bookings → lock seats → PENDING_PAYMENT
                            2. payment-service processes payment (async)
                            3. booking confirmed via Kafka → CONFIRMED
                            4. SSE push notification về client
                            """)
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT từ user-service login")));
    }
}