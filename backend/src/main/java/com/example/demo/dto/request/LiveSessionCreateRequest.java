package com.example.demo.dto.request;

import com.example.demo.entity.LiveSessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

/**
 * LIVE-01: Tạo buổi học ONLINE hoặc OFFLINE thuộc một Class.
 */
public record LiveSessionCreateRequest(

        @NotNull(message = "classId không được để trống")
        Integer classId,

        @NotBlank(message = "title không được để trống")
        @Size(max = 255) String title,

        @NotNull(message = "type không được để trống")
        LiveSessionType type,

        @NotNull(message = "startsAt không được để trống")
        LocalDateTime startsAt,

        @NotNull(message = "endsAt không được để trống")
        LocalDateTime endsAt,

        /** Địa điểm phòng học — bắt buộc khi type = OFFLINE. */
        String location,

        /** URL phòng họp provider — tùy chọn khi type = ONLINE (có thể điền sau). */
        @Pattern(regexp = "(?i)^https?://.+", message = "meetingUrl phải dùng giao thức http hoặc https")
        @Size(max = 500) String meetingUrl,

        /** External meeting ID từ provider — tùy chọn. */
        String externalMeetingId
) {}
