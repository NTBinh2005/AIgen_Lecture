package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * Entity câu hỏi trắc nghiệm được AI sinh ra cho mỗi Lecture.
 *
 * Quan hệ: N-1 với Lecture.
 * Soft-delete: thừa từ Lecture (nếu Lecture bị xóa, AI Elements không còn được query).
 */
@Entity
@Getter
@Setter
@Table(name = "ai_elements")
public class AiElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "element_id")
    private Long elementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    /** Văn bản câu hỏi */
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /**
     * Danh sách đáp án — lưu dưới dạng JSON string.
     * Ví dụ: ["A. Hà Nội", "B. TP.HCM", "C. Đà Nẵng", "D. Cần Thơ"]
     */
    @Column(name = "options", nullable = false, columnDefinition = "TEXT")
    private String options; // JSON string of List<String>

    /**
     * Đáp án đúng — lưu là ký tự đầu tiên (A/B/C/D).
     * Không expose ra ngoài với Student.
     */
    @Column(name = "correct_answer", nullable = false, length = 1)
    private String correctAnswer;

    /** Thứ tự hiển thị (optional, để sort đúng thứ tự AI sinh) */
    @Column(name = "order_index")
    private Integer orderIndex;
}
