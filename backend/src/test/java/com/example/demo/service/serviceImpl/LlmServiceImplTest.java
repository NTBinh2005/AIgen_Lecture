package com.example.demo.service.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.response.LectureGenerateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class LlmServiceImplTest {

    private LlmServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmServiceImpl();
        ReflectionTestUtils.setField(service, "geminiApiKey", "");
        ReflectionTestUtils.setField(service, "localFallbackEnabled", true);
    }

    @Test
    void generatesExactlyRequestedQuestionCountWithoutGemini() {
        LectureGenerateResponse response = service.generateLectureScript(
                "Khái niệm thứ nhất\nKhái niệm thứ hai", 7);

        assertThat(response.isAiGenerated()).isFalse();
        assertThat(response.getWarning()).isNotBlank();
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
    void fallsBackToLocalGenerationWhenGeminiRejectsApiKey() {
        ReflectionTestUtils.setField(service, "geminiApiKey", "invalid-key");
        ReflectionTestUtils.setField(service, "geminiApiUrl", "https://gemini.test/generateContent");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://gemini.test/generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        LectureGenerateResponse response = service.generateLectureScript("Nội dung hợp lệ");

        assertThat(response.isAiGenerated()).isFalse();
        assertThat(response.getWarning()).isNotBlank();
        assertThat(response.getSlides()).isNotEmpty();
        assertThat(response.getQuizzes()).hasSize(5);
        server.verify();
    }

    @Test
    void rejectsQuestionCountOutsideAllowedRange() {
        assertThatThrownBy(() -> service.generateLectureScript("Nội dung", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generateLectureScript("Nội dung", 21))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void missingKeyFailsWhenLocalFallbackIsDisabled() {
        ReflectionTestUtils.setField(service, "localFallbackEnabled", false);
        assertThatThrownBy(() -> service.generateLectureScript("Valid content"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AI_AUTH_FAILED");
    }

    @Test
    void rejectedKeyFailsWhenLocalFallbackIsDisabled() {
        ReflectionTestUtils.setField(service, "localFallbackEnabled", false);
        ReflectionTestUtils.setField(service, "geminiApiKey", "invalid-key");
        ReflectionTestUtils.setField(service, "geminiApiUrl", "https://gemini.test/auth");
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://gemini.test/auth")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.generateLectureScript("Valid content"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 403");
        server.verify();
    }

    @Test
    void languageSelectionIsIncludedInTheProviderPromptAndReturnedSlides() throws Exception {
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "geminiApiUrl", "https://gemini.test/language");
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://gemini.test/language"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(
                        org.hamcrest.Matchers.containsString("in Vietnamese")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        geminiResponse("""
                                {"slides":[{"title":"Bai hoc","bulletPoints":["Y chinh"],"narrationText":"Xin chao"}],
                                 "quizzes":[{"questionText":"Cau hoi?","options":["A","B","C","D"],"correctAnswer":"A"}]}
                                """),
                        org.springframework.http.MediaType.APPLICATION_JSON));
        var generated = service.generateLectureScript("English source document", 1, "vi");
        assertThat(generated.getSlides()).allSatisfy(slide -> assertThat(slide.getLanguage()).isEqualTo("vi"));
        server.verify();
    }

    private String geminiResponse(String content) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                java.util.Map.of("candidates", java.util.List.of(java.util.Map.of("content",
                        java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", content)))))));
    }

    @Test
    void providerMissingQuizContentFailsInsteadOfInventingTemplateQuestions() throws Exception {
        ReflectionTestUtils.setField(service, "localFallbackEnabled", false);
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "geminiApiUrl", "https://gemini.test/invalid");
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://gemini.test/invalid")).andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        geminiResponse("{\"slides\":[{\"title\":\"Valid slide\"}],\"quizzes\":[]}"),
                        org.springframework.http.MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.generateLectureScript("Source", 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("incorrect quiz question count");
        server.verify();
    }

}
