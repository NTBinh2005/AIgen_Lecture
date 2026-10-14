package com.example.demo.dto.request;

import com.example.demo.entity.LiveSessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * LIVE-01: Tạo buổi học ONLINE hoặc OFFLINE thuộc một Class.
 */
public record LiveSessionCreateRequest(

        @NotNull(message = "classId không được để trống")
        Integer classId,

        @NotBlank(message = "title không được để trống")
        String title,

        @NotNull(message = "type không được để trống")
        LiveSessionType type,

        @NotNull(message = "startsAt không được để trống")
        LocalDateTime startsAt,

        @NotNull(message = "endsAt không được để trống")
        LocalDateTime endsAt,

        /** Địa điểm phòng học — bắt buộc khi type = OFFLINE. */
        String location,

        /** URL phòng họp provider — tùy chọn khi type = ONLINE (có thể điền sau). */
        String meetingUrl,

        /** External meeting ID từ provider — tùy chọn. */
        String externalMeetingId
) {}
