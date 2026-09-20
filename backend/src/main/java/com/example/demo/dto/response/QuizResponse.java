package com.example.demo.dto.response;

import lombok.Data;
import java.util.List;

/**
 * Response trả cho client khi GET /api/lectures/{id}/quizzes.
 *
 * QUAN TRỌNG: correctAnswer bị OMIT (ẩn) với Student.
 * Dùng factory method forStudent() và forTeacher() để phân biệt.
 */
@Data
public class QuizResponse {

    private Long elementId;
    private String questionText;
    private List<String> options;
    private Integer orderIndex;

    /**
     * null khi trả về cho Student (chống gian lận).
     * Có giá trị khi trả về cho Teacher (để kiểm tra khi review).
     */
    private String correctAnswer;

    public static QuizResponse forStudent(
            Long elementId, String questionText, List<String> options, Integer orderIndex) {
        QuizResponse r = new QuizResponse();
        r.setElementId(elementId);
        r.setQuestionText(questionText);
        r.setOptions(options);
        r.setOrderIndex(orderIndex);
        r.setCorrectAnswer(null); // ẩn với student
        return r;
    }

    public static QuizResponse forTeacher(
            Long elementId, String questionText, List<String> options,
            Integer orderIndex, String correctAnswer) {
        QuizResponse r = new QuizResponse();
        r.setElementId(elementId);
        r.setQuestionText(questionText);
        r.setOptions(options);
        r.setOrderIndex(orderIndex);
        r.setCorrectAnswer(correctAnswer);
        return r;
    }
}
