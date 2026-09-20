package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request body để tạo mới một bài giảng và trigger video generation.
 * Chứa slides (cho video) và quizzes (câu hỏi trắc nghiệm, có thể null/empty).
 */
@Getter
@Setter
public class LectureCreateRequest {

    @NotBlank(message = "Tiêu đề bài giảng không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    /** Nội dung gốc trích xuất từ PDF/DOCX (hoặc text thủ công). */
    private String originalSource;

    /** Danh sách slides để render video. */
    @NotEmpty(message = "Slides không được để trống")
    private List<SlideDto> slides;

    /**
     * Danh sách câu hỏi trắc nghiệm do AI sinh ra (hoặc Teacher tự soạn).
     * Có thể null hoặc rỗng — không bắt buộc.
     */
    private List<QuizDto> quizzes;

    @Getter
    @Setter
    public static class SlideDto {
        @NotBlank(message = "Title slide không được trống")
        private String title;

        @NotEmpty
        private List<String> bulletPoints;

        @NotBlank
        private String narrationText;

        private String imagePrompt;
    }

    @Getter
    @Setter
    public static class QuizDto {
        @NotBlank(message = "Câu hỏi không được để trống")
        private String questionText;

        @NotEmpty(message = "Phải có ít nhất 1 đáp án")
        private List<String> options;

        @NotBlank(message = "Đáp án đúng không được để trống")
        @Size(min = 1, max = 1, message = "Đáp án đúng phải là 1 ký tự (A/B/C/D)")
        private String correctAnswer;
    }
}
