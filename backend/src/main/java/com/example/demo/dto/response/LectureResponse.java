package com.example.demo.dto.response;

import com.example.demo.entity.Lecture;
import com.example.demo.entity.LectureAccessScope;
import com.example.demo.entity.LectureStatus;
import com.example.demo.entity.VideoStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LectureResponse {

    private Long lectureId;
    private UUID businessId;
    private String title;
    private String originalSource;
    private String teacherName;
    private Integer teacherId;
    private LectureStatus status;
    private LectureAccessScope accessScope;
    private UUID sourceAssetId;
    private UUID currentVersionId;
    private UUID publishedVersionId;
    private int currentVersionNumber;
    private UUID latestGenerationJobId;
    private VideoStatus videoStatus;
    private String videoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private boolean canEdit;
    private boolean canPublish;
    private boolean canArchive;

    public static LectureResponse from(Lecture lecture) {
        LectureResponse dto = new LectureResponse();
        dto.setLectureId(lecture.getLectureId());
        dto.setBusinessId(lecture.getBusinessId());
        dto.setTitle(lecture.getTitle());
        dto.setOriginalSource(lecture.getOriginalSource());
        dto.setStatus(lecture.getStatus());
        dto.setAccessScope(lecture.getAccessScope());
        dto.setSourceAssetId(lecture.getSourceAssetId());
        dto.setCurrentVersionId(lecture.getCurrentVersionId());
        dto.setPublishedVersionId(lecture.getPublishedVersionId());
        dto.setCurrentVersionNumber(lecture.getCurrentVersionNumber());
        dto.setLatestGenerationJobId(lecture.getLatestGenerationJobId());
        dto.setVideoStatus(lecture.getVideoStatus());
        dto.setVideoUrl(lecture.getVideoUrl());
        dto.setCreatedAt(lecture.getCreatedAt());
        dto.setUpdatedAt(lecture.getUpdatedAt());
        dto.setPublishedAt(lecture.getPublishedAt());

        if (lecture.getTeacher() != null) {
            dto.setTeacherId(lecture.getTeacher().getUserId());
            dto.setTeacherName(lecture.getTeacher().getName());
        }
        return dto;
    }
}
