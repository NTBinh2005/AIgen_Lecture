package com.example.demo.service.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.response.LectureGenerateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LlmServiceImplTest {

    private LlmServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmServiceImpl();
        ReflectionTestUtils.setField(service, "geminiApiKey", "");
    }

    @Test
    void generatesExactlyRequestedQuestionCountWithoutGemini() {
        LectureGenerateResponse response = service.generateLectureScript(
                "Khái niệm thứ nhất\nKhái niệm thứ hai", 7);

        assertThat(response.getSlides()).isNotEmpty();
        assertThat(response.getSlides())
                .allSatisfy(slide -> {
                    assertThat(slide.getImagePrompt()).isNotBlank();
                    assertThat(slide.getLessonPhase()).isNotBlank();
                    assertThat(slide.getTeachingGoal()).isNotBlank();
                    assertThat(slide.getTeacherAction()).isNotBlank();
                });
        assertThat(response.getSlides()).extracting(LectureGenerateResponse.SlideDto::getLessonPhase)
                .contains("HOOK", "OBJECTIVE", "EXPLAIN", "EXAMPLE", "CHECK", "SUMMARY");
        assertThat(response.getSlides()).extracting(LectureGenerateResponse.SlideDto::getTeacherAction)
                .contains("WELCOME", "POINT", "EXPLAIN", "QUESTION", "SUMMARIZE");
        assertThat(response.getQuizzes()).hasSize(7);
        assertThat(response.getQuizzes())
                .allSatisfy(quiz -> {
                    assertThat(quiz.getOptions()).hasSize(4);
                    assertThat(quiz.getCorrectAnswer()).isEqualTo("A");
                });
    }

    @Test
    void defaultGenerationCreatesFiveQuestions() {
        LectureGenerateResponse response = service.generateLectureScript("Nội dung bài giảng");

        assertThat(response.getQuizzes()).hasSize(5);
    }

    @Test
    void rejectsQuestionCountOutsideAllowedRange() {
        assertThatThrownBy(() -> service.generateLectureScript("Nội dung", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generateLectureScript("Nội dung", 21))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
