package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record PresentationCreateFromLectureRequest(
        @NotNull UUID lectureVersionId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100) String templateId,
        Map<String, Object> parameters
) {
}

