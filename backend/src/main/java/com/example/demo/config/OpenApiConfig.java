package com.example.demo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AI Gen Lecture API",
                version = "v1",
                description = "Basic APIs for users, classes, and class enrollments."
        )
)
public class OpenApiConfig {
}
