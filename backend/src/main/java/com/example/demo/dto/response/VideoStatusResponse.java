package com.example.demo.dto.response;

import com.example.demo.entity.VideoStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO cho endpoint GET /api/lectures/{id}/video-status.
 * FE/Mobile dùng để poll trạng thái render video.
 */
@Getter
@Setter
public class VideoStatusResponse {

    private Long lectureId;
    private VideoStatus videoStatus;

    /** URL tới file video MP4. null nếu chưa done. */
    private String videoUrl;

    /** Thông báo lỗi nếu status = FAILED. null nếu không lỗi. */
    private String errorMessage;

    public static VideoStatusResponse from(Long lectureId, VideoStatus status,
                                           String videoUrl, String errorMessage) {
        VideoStatusResponse dto = new VideoStatusResponse();
        dto.setLectureId(lectureId);
        dto.setVideoStatus(status);
        dto.setVideoUrl(videoUrl);
        dto.setErrorMessage(errorMessage);
        return dto;
    }
}
