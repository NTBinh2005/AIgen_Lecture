package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.service.LlmService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmServiceImpl implements LlmService {
    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String separator = geminiApiUrl.contains("?")
                ? (geminiApiUrl.endsWith("?") || geminiApiUrl.endsWith("&") ? "" : "&")
                : "?";
        String url = geminiApiUrl + separator + "key=" + geminiApiKey;

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
}
