package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * @param data slide gốc theo schema của slide-ai-service; null với slide thường
 *             (không ghi vào slides_json để dữ liệu cũ giữ nguyên dạng).
 *             Kiểu Map (không phải JsonNode) để cả Jackson 2 lẫn Jackson 3 (HTTP) đọc/ghi được.
 */
public record PresentationSlideResponse(
        String title,
        List<String> contentBlocks,
        String speakerNotes,
        String layout,
        int order,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> data
) {
    public PresentationSlideResponse(
            String title, List<String> contentBlocks, String speakerNotes, String layout, int order) {
        this(title, contentBlocks, speakerNotes, layout, order, null);
    }
}
