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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Ghi nhận từng lần join/leave của người tham gia online từ provider webhook.
 * Dùng để tổng hợp attendance online (LIVE-05) và quản lý người tham gia (LIVE-03).
 *
 * LIVE-BR-02: Attendance tính theo tổng thời lượng tham dự.
 * LIVE-BR-05: Dữ liệu này đến qua webhook đã xác thực.
 */
@Entity
@Getter
@Setter
@Table(name = "session_participants")
public class SessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    /** Null nếu là guest (không có tài khoản). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** ID participant do provider gán — dùng để match sự kiện leave. */
    @Column(name = "external_participant_id", length = 255)
    private String externalParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SessionParticipantRole role;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "muted", nullable = false)
    private boolean muted;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (role == null) role = SessionParticipantRole.ATTENDEE;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
