package com.example.demo.dto.response;

import com.example.demo.entity.SessionResource;
import com.example.demo.entity.SessionResourceType;
import java.time.LocalDateTime;

public record SessionResourceResponse(
        Long id,
        Long sessionId,
        SessionResourceType resourceType,
        Long resourceId,
        String title,
        boolean downloadAllowed,
        LocalDateTime createdAt
) {
    public static SessionResourceResponse from(SessionResource value) {
        return new SessionResourceResponse(value.getId(), value.getSession().getSessionId(),
                value.getResourceType(), value.getResourceId(), value.getTitle(),
                value.isDownloadAllowed(), value.getCreatedAt());
    }
}
