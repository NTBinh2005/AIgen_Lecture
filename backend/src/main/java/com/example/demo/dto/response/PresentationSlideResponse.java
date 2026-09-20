package com.example.demo.dto.response;

import java.util.List;

public record PresentationSlideResponse(
        String title,
        List<String> contentBlocks,
        String speakerNotes,
        String layout,
        int order
) {
}
