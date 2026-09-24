package com.example.demo.entity;

import com.example.demo.entity.LiveSessionType;
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
 * Đại diện cho một buổi học (online hoặc offline) thuộc một Class.
 * Vòng đời: SCHEDULED -> OPEN -> LIVE -> ENDED; nhánh CANCELLED.
 *
 * LIVE-01: Lập lịch buổi học.
 * LIVE-08: Đổi hoặc hủy lịch.
 */
@Entity
@Getter
@Setter
@Table(name = "live_sessions")
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private LiveSessionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LiveSessionStatus status;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /** Phòng học / địa điểm — dùng cho OFFLINE. */
    @Column(name = "location", length = 255)
    private String location;

    /** URL phòng họp do provider cấp — dùng cho ONLINE. */
    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    /** ID phòng/meeting bên phía provider để đối chiếu webhook. */
    @Column(name = "external_meeting_id", length = 255)
    private String externalMeetingId;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = LiveSessionStatus.SCHEDULED;
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
