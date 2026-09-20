package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Entity bài giảng (Lecture).
 *
 * Field theo PROJECT_CONTEXT.md (LectureID, TeacherID, Title, OriginalSource, CreatedDate)
 * + bổ sung theo pivot: videoUrl, videoStatus, videoJobId (lưu jobId từ video-service để poll).
 *
 * Soft-delete theo BR-08: không hard-delete, chỉ đổi deletedAt.
 */
@Entity
@Getter
@Setter
@SQLDelete(sql = "UPDATE lectures SET deleted_at = NOW() WHERE lecture_id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "lectures", indexes = {
        @Index(name = "idx_lecture_business_id", columnList = "business_id", unique = true),
        @Index(name = "idx_lecture_owner_status", columnList = "teacher_id,status")
})
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lecture_id")
    private Long lectureId;

    /** Stable UUID used by new cross-module contracts while retaining the legacy numeric PK. */
    @Column(name = "business_id", nullable = false, updatable = false, unique = true)
    private UUID businessId;

    /**
     * Teacher sở hữu bài giảng — FK tới User.
     * Dùng @ManyToOne lazy để tránh N+1 query.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * Nội dung gốc trích xuất từ PDF/DOCX (hoặc script JSON từ LLM).
     * Dạng TEXT để chứa nội dung dài.
     */
    @Column(name = "original_source", columnDefinition = "TEXT")
    private String originalSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LectureStatus status = LectureStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", nullable = false, length = 20)
    private LectureAccessScope accessScope = LectureAccessScope.PRIVATE;

    @Column(name = "source_asset_id")
    private UUID sourceAssetId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "published_version_id")
    private UUID publishedVersionId;

    @Column(name = "current_version_number", nullable = false)
    private int currentVersionNumber;

    @Column(name = "latest_generation_job_id")
    private UUID latestGenerationJobId;

    // ── Video pivot fields ────────────────────────────────────────────────────

    /**
     * Job ID trả về từ video-service (dùng để poll trạng thái).
     * null khi chưa gửi render request.
     */
    @Column(name = "video_job_id", length = 36)
    private String videoJobId;

    /**
     * URL tới file video MP4 (có sau khi videoStatus = DONE).
     * Giai đoạn 1: URL local của video-service.
     * Giai đoạn 2: URL Firebase Storage.
     */
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /**
     * Trạng thái render video.
     * Default PENDING — chưa gửi yêu cầu render.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", nullable = false, length = 20)
    private VideoStatus videoStatus = VideoStatus.PENDING;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Soft-delete timestamp. null = chưa bị xóa. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        if (businessId == null) {
            businessId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (videoStatus == null) {
            videoStatus = VideoStatus.PENDING;
        }
        if (status == null) {
            status = LectureStatus.DRAFT;
        }
        if (accessScope == null) {
            accessScope = LectureAccessScope.PRIVATE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
