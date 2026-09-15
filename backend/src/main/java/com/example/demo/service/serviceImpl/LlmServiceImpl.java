package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class LlmServiceImpl implements LlmService {
    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmServiceImpl () {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Gửi nội dung tài liệu cho Gemini, nhận về kịch bản bài giảng gồm slides + quizzes.
     * Chỉ gọi API 1 lần duy nhất, không gọi 2 lần riêng.
     * quizzes có thể null/empty nếu Gemini không sinh ra — không crash.
     */
    public LectureGenerateResponse generateLectureScript(String documentText) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key is not configured.");
        }

        String prompt = "Bạn là trợ lý giáo dục. Phân tích nội dung tài liệu và tạo ra:\n"
                + "1. Cấu trúc bài giảng gồm 3-7 slides\n"
                + "2. Bộ 3-5 câu hỏi trắc nghiệm 4 đáp án để kiểm tra kiến thức\n\n"
                + "Quy tắc CỰC KỲ QUAN TRỌNG: Trả về JSON chuẩn xác (không markdown, không backtick). "
                + "Đảm bảo mọi ký tự xuống dòng (newline) bên trong nội dung chữ (như lời thoại) PHẢI ĐƯỢC ESCAPE thành \\\\n, tuyệt đối không được gõ phím Enter ngắt dòng bên trong chuỗi JSON.\n\n"
                + "Format bắt buộc:\n"
                + "{\n"
                + "  \"slides\": [\n"
                + "    {\n"
                + "      \"title\": \"Tiêu đề slide\",\n"
                + "      \"bulletPoints\": [\"Điểm chính 1\", \"Điểm chính 2\"],\n"
                + "      \"narrationText\": \"Lời thoại cho slide này, 2-3 câu.\",\n"
                + "      \"imagePrompt\": \"1 short English phrase describing the slide visual, e.g., 'a spinning earth in space, cinematic 4k'\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"quizzes\": [\n"
                + "    {\n"
                + "      \"questionText\": \"Câu hỏi?\",\n"
                + "      \"options\": [\"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\"],\n"
                + "      \"correctAnswer\": \"A\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"
                + "Nội dung tài liệu:\n" + documentText;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);

        Map<String, Object> contents = new HashMap<>();
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        // Cấu hình bắt buộc Gemini trả về JSON hợp lệ (có ở v1beta)
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = geminiApiUrl + geminiApiKey;

        try {
            String response = restTemplate.postForObject(url, entity, String.class);
            JsonNode rootNode = objectMapper.readTree(response);

            // Lấy text từ response của Gemini
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                String aiText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

                // Dọn dẹp markdown nếu LLM vô tình trả về (e.g., ```json ... ```)
                aiText = aiText.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)^```\\s*", "").replaceAll("```$", "").trim();

                LectureGenerateResponse result = objectMapper.readValue(aiText, LectureGenerateResponse.class);

                // Đảm bảo quizzes không null để tránh NullPointerException ở downstream
                if (result.getQuizzes() == null) {
                    result.setQuizzes(List.of());
                }
                return result;
            } else {
                throw new RuntimeException("Invalid response from Gemini API.");
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new RuntimeException("AI service timeout, vui lòng thử lại", e);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new RuntimeException("AI service timeout, vui lòng thử lại", e);
            }
            throw new RuntimeException("AI processing failed: " + e.getMessage(), e);
        }
    }
}
