package com.example.demo.service;

import com.example.demo.dto.response.LectureGenerateResponse;

public interface LlmService {
    LectureGenerateResponse generateLectureScript(String documentText);
    
    /**
     * Dùng cho QUIZ-01: Sinh câu hỏi từ nội dung bài giảng.
     */
    String generateQuizDraft(String documentText, String configPrompt);
    
    /**
     * Dùng cho QUIZ-07: AI chấm điểm câu tự luận.
     * Trả về điểm dạng Double (ví dụ 8.5)
     */
    Double aiSuggestScore(String questionText, String studentAnswer, String rubric);
}
