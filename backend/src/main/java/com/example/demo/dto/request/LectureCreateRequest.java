package com.example.demo.dto.request;

import com.example.demo.entity.LectureAccessScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Request for manually creating a lecture. Slides remain optional legacy video input. */
@Getter
@Setter
public class LectureCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 2_000_000, message = "Content is too large")
    private String originalSource;

    private LectureAccessScope accessScope = LectureAccessScope.PRIVATE;

    @Valid
    private List<SlideDto> slides;

    /** Legacy field retained for API compatibility; quiz ownership remains Backend 4. */
    @Valid
    private List<QuizDto> quizzes;

    @Getter
    @Setter
    public static class SlideDto {
        @NotBlank(message = "Slide title is required")
        @Size(max = 255)
        private String title;

        @NotEmpty(message = "A slide needs at least one content block")
        private List<@NotBlank @Size(max = 2_000) String> bulletPoints;

        @Size(max = 20_000)
        private String narrationText;

        @Size(max = 2_000)
        private String imagePrompt;
    }

    @Getter
    @Setter
    public static class QuizDto {
        @NotBlank
        private String questionText;

        @NotEmpty
        private List<String> options;

        @NotBlank
        @Size(min = 1, max = 1)
        private String correctAnswer;
    }
}
