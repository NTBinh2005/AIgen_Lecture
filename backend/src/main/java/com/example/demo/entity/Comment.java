package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity bình luận / Q&A dưới mỗi bài giảng.
 *
 * Quan hệ:
 *   - N-1 với Lecture (mỗi comment thuộc 1 bài giảng)
 *   - N-1 với User (ai viết comment)
 *   - N-1 tự thân (parentComment) để hỗ trợ reply 1 cấp
 */
@Entity
@Getter
@Setter
@Table(
    name = "comments",
    indexes = {
        @Index(name = "idx_comment_lecture", columnList = "lecture_id"),
        @Index(name = "idx_comment_user",    columnList = "user_id")
    }
)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Reply 1 cấp: null nghĩa là comment gốc.
     * Nếu có giá trị → đây là reply cho comment cha.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
    }
}
