package com.ticketmaster.user.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 (Swagger) documentation configuration cho user-service.
 *
 * <p>Swagger UI: <a href="http://localhost:8081/swagger-ui.html">http://localhost:8081/swagger-ui.html</a>
 * <p>OpenAPI JSON: <a href="http://localhost:8081/v3/api-docs">http://localhost:8081/v3/api-docs</a>
 *
 * <p>Cấu hình JWT Bearer token trong Swagger UI:
 * Click "Authorize" → nhập token → mọi request sẽ tự thêm {@code Authorization: Bearer {token}}.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketmaster – User Service API")
                        .description("Identity & Access Management: đăng ký, đăng nhập, quản lý profile người dùng")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ticketmaster Dev Team")
                                .email("dev@ticketmaster.com")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT access token (không cần 'Bearer ' prefix)")));
    }
}