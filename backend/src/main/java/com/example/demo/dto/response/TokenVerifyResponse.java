package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response payload containing the verified user identity, role, and granted permissions")
public record TokenVerifyResponse(
        @Schema(description = "Whether the token is valid and active", example = "true")
        boolean valid,

        @Schema(description = "User ID", example = "123")
        Integer userId,

        @Schema(description = "User email or identifier", example = "student@example.com")
        String email,

        @Schema(description = "User role", example = "STUDENT")
        String role,

        @Schema(description = "List of permissions assigned to this user", example = "[\"COURSE_VIEW\", \"COURSE_ENROLL\"]")
        List<String> permissions,

        @Schema(description = "Message or reason if token is invalid", example = "Token is valid")
        String message
) {}
