package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata về bản ghi (recording) của một live session từ provider bên ngoài.
 * MVP không lưu file media trực tiếp (LIVE-BR-06).
 *
 * LIVE-07: Ghi hình — nhận metadata và trạng thái từ provider qua webhook.
 * LIVE-BR-04: Quyền xem recording kế thừa theo Class.
 */
@Entity
@Getter
@Setter
@Table(name = "session_recordings")
public class SessionRecording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recording_id")
    private Long recordingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    /** ID của recording bên phía provider; dùng để đối chiếu webhook. */
    @Column(name = "external_recording_id", length = 255)
    private String externalRecordingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordingStatus status;

    /** URL phát lại do provider cấp; chỉ có giá trị khi status = READY. */
    @Column(name = "playback_url", length = 500)
    private String playbackUrl;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Policy LIVE-BR-04: cần thông báo và đồng ý trước khi ghi. */
    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = RecordingStatus.REQUESTED;
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
