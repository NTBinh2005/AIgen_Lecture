package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PresentationSlideRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 4_000) String> contentBlocks,
        @Size(max = 20_000) String speakerNotes,
        @NotBlank @Size(max = 50) String layout,
        @PositiveOrZero int order
) {
}
