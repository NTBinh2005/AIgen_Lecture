package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * @param data slide gốc theo schema của slide-ai-service (bố cục COMPARE, TABLE...);
 *             null với slide thường. Dùng để dựng HTML trình chiếu.
 *             Kiểu Map (không phải JsonNode) để cả Jackson 2 lẫn Jackson 3 (HTTP) đọc/ghi được.
 */
public record PresentationSlideRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 4_000) String> contentBlocks,
        @Size(max = 20_000) String speakerNotes,
        @NotBlank @Size(max = 50) String layout,
        @PositiveOrZero int order,
        Map<String, Object> data
) {
    public PresentationSlideRequest(
            String title, List<String> contentBlocks, String speakerNotes, String layout, int order) {
        this(title, contentBlocks, speakerNotes, layout, order, null);
    }
}
