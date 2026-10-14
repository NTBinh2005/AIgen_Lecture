package com.example.demo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PresentationUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotEmpty @Size(max = 200) List<@Valid PresentationSlideRequest> slides
) {
}
