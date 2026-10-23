package com.example.demo.dto.request;

import com.example.demo.entity.LiveSessionType;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * LIVE-08: Cập nhật thông tin buổi học (partial update).
 * Tất cả field đều nullable — chỉ field nào không null mới được cập nhật.
 */
public record LiveSessionUpdateRequest(
        @Size(max = 255) String title,
        LiveSessionType type,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String location,
        @Pattern(regexp = "(?i)^https?://.+", message = "meetingUrl phải dùng giao thức http hoặc https")
        @Size(max = 500) String meetingUrl,
        String externalMeetingId
) {}
