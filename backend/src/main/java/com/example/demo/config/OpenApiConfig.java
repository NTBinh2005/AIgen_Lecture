package com.example.demo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AI Gen Lecture API",
                version = "v1",
                description = "Mọi trường LocalDateTime (openAt, closeAt, startsAt, endsAt, deadlineAt...) dùng UTC, dạng yyyy-MM-ddTHH:mm:ss. Client phải chuyển giờ địa phương sang UTC trước khi gửi và ngược lại khi hiển thị. Các trường Instant dùng ISO-8601 có Z."
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Nhập JWT access token",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
