package com.example.demo.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class LectureGenerateResponse {

    private List<SlideDto> slides;

    /** Có thể null nếu Gemini không sinh ra quiz — xử lý an toàn ở frontend */
    private List<QuizDto> quizzes;

    @Data
    public static class SlideDto {
        private String title;
        private List<String> bulletPoints;
        private String narrationText;
        private String imagePrompt;
        /** HOOK, OBJECTIVE, EXPLAIN, EXAMPLE, CHECK, or SUMMARY. */
        private String lessonPhase;
        /** What the learner should understand after this scene. */
        private String teachingGoal;
        /** WELCOME, EXPLAIN, POINT, EMPHASIZE, QUESTION, or SUMMARIZE. */
        private String teacherAction;
        /** Optional question/instruction shown while the teacher waits for learners. */
        private String interactionPrompt;
    }

    @Data
    public static class QuizDto {
        private String questionText;
        /** 4 đáp án dạng: ["A. ...", "B. ...", "C. ...", "D. ..."] */
        private List<String> options;
        /** Ký tự đáp án đúng: "A", "B", "C", hoặc "D" */
        private String correctAnswer;
    }
}
