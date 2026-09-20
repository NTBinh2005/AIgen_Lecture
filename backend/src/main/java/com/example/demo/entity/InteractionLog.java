package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity lưu lịch sử tương tác của Student với câu hỏi trắc nghiệm.
 *
 * BR-06: isFirstAttempt = true chỉ ở lần đầu tiên Student trả lời một câu.
 * Các lần làm lại sau = false, không tính vào Analytics điểm TB.
 */
@Entity
@Getter
@Setter
@Table(
    name = "interaction_logs",
    indexes = {
        @Index(name = "idx_interaction_student", columnList = "student_id"),
        @Index(name = "idx_interaction_element", columnList = "element_id")
    }
)
public class InteractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false)
    private AiElement aiElement;

    /** Đáp án mà học sinh đã chọn (A/B/C/D) */
    @Column(name = "submitted_answer", nullable = false, length = 1)
    private String submittedAnswer;

    /** Backend tự tính: submittedAnswer.equals(aiElement.correctAnswer) */
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    /**
     * Thời gian học sinh dành để đọc câu hỏi (giây).
     * Frontend gửi lên, Backend tin tưởng (không phải điểm bảo mật cao).
     */
    @Column(name = "time_spent")
    private Integer timeSpent;

    /**
     * BR-06: true nếu đây là lần đầu tiên student trả lời câu hỏi này.
     * Backend tự xác định bằng cách query xem đã có log trước đó chưa.
     */
    @Column(name = "is_first_attempt", nullable = false)
    private Boolean isFirstAttempt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    void prePersist() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }
}
