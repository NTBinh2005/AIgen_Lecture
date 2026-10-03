package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.service.LlmService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {
    /**
     * Base retry back-off in milliseconds. Gemini trả 429/500/503 khi quá tải; ta thử lại
     * với thời gian chờ tăng dần (3s, 6s, 12s) trước khi để job FAILED.
     */
    private static final long[] RETRY_BACKOFF_MS = {3_000L, 6_000L, 12_000L};

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
        if (!StringUtils.hasText(geminiApiKey)) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        if (!StringUtils.hasText(documentText)) {
            throw new IllegalArgumentException("Document text is required");
        }

        String prompt = "You are an education content assistant. Convert the following source into "
                + "a concise lecture of 3 to 7 slides. Return strict JSON without markdown using this schema: "
                + "{\"slides\":[{\"title\":\"Slide title\",\"bulletPoints\":[\"Point\"],"
                + "\"narrationText\":\"Speaker notes\",\"imagePrompt\":\"Optional visual description\"}]}. "
                + "Do not generate quizzes or publish-ready decisions. Source:\n" + documentText;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

        try {
            String response = postToGemini(requestBody);
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
            // Quiz is owned by Backend 4 and is intentionally never generated here.
            result.setQuizzes(List.of());
            return result;
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("AI service timeout; please retry", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("AI processing failed", exception);
        }
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

        try {
            String response = postToGemini(requestBody);
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

    /**
     * Gọi Gemini với API key trong header {@code x-goog-api-key} (không để trong URL để
     * tránh lộ key trong log/thông báo lỗi), và thử lại với back-off khi gặp 429/500/503
     * hoặc timeout tạm thời.
     */
    private String postToGemini(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        int attempt = 0;
        while (true) {
            try {
                return restTemplate.postForObject(geminiApiUrl, entity, String.class);
            } catch (HttpStatusCodeException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status == 500 || status == 503;
                if (!retryable || attempt >= RETRY_BACKOFF_MS.length) {
                    throw exception;
                }
                log.warn("Gemini trả {} (lần {}/{}), thử lại sau {}ms",
                        status, attempt + 1, RETRY_BACKOFF_MS.length, RETRY_BACKOFF_MS[attempt]);
                sleep(RETRY_BACKOFF_MS[attempt]);
                attempt++;
            } catch (ResourceAccessException exception) {
                if (attempt >= RETRY_BACKOFF_MS.length) {
                    throw exception;
                }
                log.warn("Gemini timeout/không kết nối được (lần {}/{}), thử lại sau {}ms",
                        attempt + 1, RETRY_BACKOFF_MS.length, RETRY_BACKOFF_MS[attempt]);
                sleep(RETRY_BACKOFF_MS[attempt]);
                attempt++;
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI retry interrupted", interrupted);
        }
    }
}
