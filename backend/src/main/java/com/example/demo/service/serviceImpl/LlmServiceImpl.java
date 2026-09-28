package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.service.LlmService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {
    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 20;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000); // Tăng timeout lên 120s cho Gemini
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public LectureGenerateResponse generateLectureScript(String documentText) {
        return generateLectureScript(documentText, DEFAULT_QUESTION_COUNT);
    }

    @Override
    public LectureGenerateResponse generateLectureScript(String documentText, int questionCount) {
        String apiKey = geminiApiKey;
        if (!StringUtils.hasText(documentText)) {
            throw new IllegalArgumentException("Document text is required");
        }
        if (questionCount < 1 || questionCount > MAX_QUESTION_COUNT) {
            throw new IllegalArgumentException("Question count must be between 1 and " + MAX_QUESTION_COUNT);
        }

        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini API key is not configured. Falling back to document text parser.");
            return generateFallbackResponse(documentText, questionCount);
        }

        String prompt = "You are an education content assistant. Convert the following source document into "
                + "a concise lecture of 3 to 7 slides and exactly " + questionCount
                + " multiple-choice quiz questions. The quizzes array must contain exactly "
                + questionCount + " items. Return strict JSON without markdown using this schema: "
                + "{\"slides\":[{\"title\":\"Slide title\",\"bulletPoints\":[\"Point 1\",\"Point 2\"],"
                + "\"narrationText\":\"Speaker notes narration\",\"imagePrompt\":\"Optional visual description\"}], "
                + "\"quizzes\":[{\"questionText\":\"Question text?\",\"options\":[\"A. Option 1\",\"B. Option 2\",\"C. Option 3\",\"D. Option 4\"],\"correctAnswer\":\"A\"}]}. "
                + "Source:\n" + documentText;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String separator = geminiApiUrl.contains("?")
                ? (geminiApiUrl.endsWith("?") || geminiApiUrl.endsWith("&") ? "" : "&")
                : "?";
        String url = geminiApiUrl + separator + "key=" + apiKey;

        try {
            String response = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("AI provider returned no generated content");
            }
            String aiText = parts.get(0).path("text").asText();
            aiText = aiText.replaceFirst("(?s)^```(?:json)?\\s*", "")
                    .replaceFirst("(?s)```\\s*$", "")
                    .trim();
            LectureGenerateResponse result = objectMapper.readValue(aiText, LectureGenerateResponse.class);
            if (result.getSlides() == null || result.getSlides().isEmpty()) {
                throw new IllegalStateException("AI provider returned no slides");
            }
            ensureQuestionCount(result, documentText, questionCount);
            return result;
        } catch (Exception exception) {
            log.warn("Gemini AI generation failed ({}), generating fallback slides from document text.", exception.getMessage());
            return generateFallbackResponse(documentText, questionCount);
        }
    }

    private LectureGenerateResponse generateFallbackResponse(String documentText, int questionCount) {
        LectureGenerateResponse response = new LectureGenerateResponse();
        List<LectureGenerateResponse.SlideDto> slides = new ArrayList<>();

        List<String> paragraphs = extractParagraphs(documentText);
        int count = Math.min(Math.max(paragraphs.size(), 1), 6);
        for (int i = 0; i < count; i++) {
            String p = paragraphs.get(i).trim();
            if (p.isBlank()) continue;
            LectureGenerateResponse.SlideDto slide = new LectureGenerateResponse.SlideDto();
            String firstLine = p.split("\n")[0];
            if (firstLine.length() > 60) firstLine = firstLine.substring(0, 57) + "...";
            slide.setTitle(firstLine.isBlank() ? "Slide " + (i + 1) : firstLine);
            slide.setBulletPoints(List.of(p.length() > 200 ? p.substring(0, 197) + "..." : p));
            slide.setNarrationText(p);
            slide.setImagePrompt("Modern educational illustration about " + firstLine
                    + ", clean composition, classroom friendly, no text, 16:9");
            slides.add(slide);
        }
        if (slides.isEmpty()) {
            LectureGenerateResponse.SlideDto slide = new LectureGenerateResponse.SlideDto();
            slide.setTitle("Giới thiệu bài giảng");
            slide.setBulletPoints(List.of("Nội dung bài giảng từ tài liệu"));
            slide.setNarrationText("Nội dung bài giảng từ tài liệu đã upload");
            slide.setImagePrompt("Friendly AI education robot teaching in a modern classroom, no text, 16:9");
            slides.add(slide);
        }
        List<LectureGenerateResponse.QuizDto> quizzes = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            quizzes.add(createFallbackQuiz(paragraphs, i));
        }
        response.setSlides(slides);
        response.setQuizzes(quizzes);
        return response;
    }

    private void ensureQuestionCount(
            LectureGenerateResponse response,
            String documentText,
            int questionCount) {
        List<LectureGenerateResponse.QuizDto> quizzes = response.getQuizzes() == null
                ? new ArrayList<>()
                : new ArrayList<>(response.getQuizzes());
        if (quizzes.size() > questionCount) {
            quizzes = new ArrayList<>(quizzes.subList(0, questionCount));
        }

        List<String> paragraphs = extractParagraphs(documentText);
        while (quizzes.size() < questionCount) {
            quizzes.add(createFallbackQuiz(paragraphs, quizzes.size()));
        }
        response.setQuizzes(quizzes);
    }

    private List<String> extractParagraphs(String documentText) {
        return documentText.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private LectureGenerateResponse.QuizDto createFallbackQuiz(List<String> paragraphs, int index) {
        String paragraph = paragraphs.isEmpty()
                ? "Nội dung bài giảng từ tài liệu được upload"
                : paragraphs.get(index % paragraphs.size());
        String topic = paragraph.length() > 60 ? paragraph.substring(0, 57) + "..." : paragraph;
        String answer = paragraph.length() > 70 ? paragraph.substring(0, 67) + "..." : paragraph;

        LectureGenerateResponse.QuizDto quiz = new LectureGenerateResponse.QuizDto();
        quiz.setQuestionText("Câu " + (index + 1) + ": Nội dung trọng tâm của phần \"" + topic + "\" là gì?");
        quiz.setOptions(List.of(
                "A. " + answer,
                "B. Khái niệm không xuất hiện trong tài liệu",
                "C. Số liệu không liên quan đến bài giảng",
                "D. Nội dung chưa được xác định"
        ));
        quiz.setCorrectAnswer("A");
        return quiz;
    }

    @Override
    public String generateQuizDraft(String documentText, String configPrompt) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key is not configured.");
        }

        String prompt = "Bạn là trợ lý giáo dục. Dựa vào nội dung tài liệu sau, hãy tạo ra danh sách các câu hỏi trắc nghiệm.\n"
                + (configPrompt != null ? configPrompt : "Tạo 5 câu hỏi MCQ_SINGLE.") + "\n\n"
                + "Quy tắc CỰC KỲ QUAN TRỌNG: Trả về JSON chuẩn xác là một mảng (Array). KHÔNG trả về object chứa mảng. KHÔNG kèm markdown (không ```json).\n"
                + "Format bắt buộc:\n"
                + "[\n"
                + "  {\n"
                + "    \"questionType\": \"MCQ_SINGLE\",\n"
                + "    \"questionText\": \"Câu hỏi?\",\n"
                + "    \"options\": [\"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\"],\n"
                + "    \"correctAnswer\": \"A\",\n"
                + "    \"points\": 1,\n"
                + "    \"explanation\": \"Giải thích vì sao đúng...\"\n"
                + "  }\n"
                + "]\n\n"
                + "Nội dung tài liệu:\n" + documentText;

        return callGeminiApi(prompt);
    }

    @Override
    public Double aiSuggestScore(String questionText, String studentAnswer, String rubric) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            return null; // Silent degrade if not configured
        }

        String prompt = "Bạn là người chấm điểm tự luận.\n"
                + "Câu hỏi: " + questionText + "\n"
                + "Hướng dẫn chấm (Rubric): " + (rubric != null ? rubric : "Không có") + "\n"
                + "Bài làm của học sinh (đã loại bỏ thông tin cá nhân): " + studentAnswer + "\n\n"
                + "YÊU CẦU: Dựa trên câu trả lời, hãy đề xuất 1 điểm số. Chỉ trả về một con số thập phân, ví dụ 8.5, tuyệt đối không trả về chữ nào khác.";

        try {
            String responseText = callGeminiApi(prompt);
            return Double.parseDouble(responseText.trim());
        } catch (Exception e) {
            return null; // AI suggestion is not critical
        }
    }

    private String callGeminiApi(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);

        Map<String, Object> contents = new HashMap<>();
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = geminiApiUrl + "?key=" + geminiApiKey;

        try {
            String response = restTemplate.postForObject(url, entity, String.class);
            JsonNode rootNode = objectMapper.readTree(response);

            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                String aiText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                return aiText.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)^```\\s*", "").replaceAll("```$", "").trim();
            } else {
                throw new RuntimeException("Invalid response from Gemini API.");
            }
        } catch (Exception e) {
            throw new RuntimeException("AI processing failed: " + e.getMessage(), e);
        }
    }
}
