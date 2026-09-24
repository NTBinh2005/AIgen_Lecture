package com.example.demo.dto.request;

import com.example.demo.entity.LiveSessionType;
import java.time.LocalDateTime;

/**
 * LIVE-08: Cập nhật thông tin buổi học (partial update).
 * Tất cả field đều nullable — chỉ field nào không null mới được cập nhật.
 */
public record LiveSessionUpdateRequest(
        String title,
        LiveSessionType type,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String location,
        String meetingUrl,
        String externalMeetingId
) {}
