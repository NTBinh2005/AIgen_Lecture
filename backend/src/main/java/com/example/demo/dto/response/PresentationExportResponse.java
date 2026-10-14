package com.example.demo.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PresentationExportResponse(
        UUID exportId,
        UUID presentationId,
        UUID versionId,
        String fileName,
        String contentType,
        long byteSize,
        String sha256,
        Instant createdAt
) {
}
