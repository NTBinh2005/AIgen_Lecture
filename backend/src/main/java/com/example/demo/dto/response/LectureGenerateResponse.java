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
