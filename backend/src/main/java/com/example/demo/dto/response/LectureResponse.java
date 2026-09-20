package com.example.demo.dto.response;

import com.example.demo.entity.Lecture;
import com.example.demo.entity.VideoStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Response DTO cho Lecture — trả về cho FE và Mobile.
 * Không expose internal fields như videoJobId, deletedAt.
 */
@Getter
@Setter
public class LectureResponse {

    private Long lectureId;
    private String title;
    private String originalSource;
    private String teacherName;
    private Integer teacherId;

    // Video fields
    private VideoStatus videoStatus;
    private String videoUrl;

    // Timestamps
    private LocalDateTime createdAt;

    /**
     * Static factory — chuyển đổi từ entity sang DTO.
     */
    public static LectureResponse from(Lecture lecture) {
        LectureResponse dto = new LectureResponse();
        dto.setLectureId(lecture.getLectureId());
        dto.setTitle(lecture.getTitle());
        dto.setOriginalSource(lecture.getOriginalSource());
        dto.setVideoStatus(lecture.getVideoStatus());
        dto.setVideoUrl(lecture.getVideoUrl());
        dto.setCreatedAt(lecture.getCreatedAt());

        if (lecture.getTeacher() != null) {
            dto.setTeacherId(lecture.getTeacher().getUserId());
            dto.setTeacherName(lecture.getTeacher().getName());
        }

        return dto;
    }
}
