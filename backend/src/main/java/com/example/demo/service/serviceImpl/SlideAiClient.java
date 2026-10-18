package com.example.demo.service.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Gọi slide-ai-service (Python): sinh deck JSON nhiều bố cục bằng Ollama/Gemini và dựng HTML trình chiếu.
 * Tắt khi không đặt SLIDE_AI_SERVICE_URL; khi đó tạo slide AI dùng luồng Gemini cũ.
 */
@Component
public class SlideAiClient {
    private static final String TOKEN_HEADER = "X-Slide-AI-Token";

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;
    private final RestTemplate generateTemplate;
    private final RestTemplate renderTemplate;

    public SlideAiClient(
            ObjectMapper objectMapper,
            @Value("${slide.ai.service.url:}") String baseUrl,
            @Value("${slide.ai.service.token:}") String token,
            @Value("${slide.ai.service.generate-timeout-seconds:900}") long generateTimeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim().replaceAll("/+$", "") : "";
        this.token = token == null ? "" : token.trim();
        // Ollama trên GPU 6GB mất vài phút cho một bộ slide, nên không dùng RestTemplate chung (không timeout).
        this.generateTemplate = template(Duration.ofSeconds(generateTimeoutSeconds));
        this.renderTemplate = template(Duration.ofSeconds(30));
    }

    public boolean isEnabled() {
        return !baseUrl.isEmpty();
    }

    /** Trả về node "deck" ({meta, slides}) theo schema của slide-ai-service. */
    public JsonNode generate(String text, int slides, String audience, String theme) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("text", text)
                .put("slides", slides)
                .put("audience", audience)
                .put("theme", theme);
        JsonNode response = readTree(post(generateTemplate, "/generate", body));
        JsonNode deck = response.path("deck");
        if (!deck.path("slides").isArray() || deck.path("slides").isEmpty()) {
            throw new SlideAiException("slide-ai-service không trả về slide nào");
        }
        return deck;
    }

    public String render(JsonNode deck, String theme) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("deck", deck);
        body.put("theme", theme);
        return post(renderTemplate, "/render", body);
    }

    private String post(RestTemplate template, String path, JsonNode body) {
        if (!isEnabled()) {
            throw new SlideAiException("Chưa cấu hình slide-ai-service");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML));
        if (!token.isEmpty()) {
            headers.set(TOKEN_HEADER, token);
        }
        try {
            byte[] bytes = template.postForObject(
                    baseUrl + path,
                    new HttpEntity<>(objectMapper.writeValueAsBytes(body), headers),
                    byte[].class);
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (RestClientResponseException exception) {
            throw new SlideAiException(
                    "slide-ai-service trả lỗi HTTP " + exception.getStatusCode().value(), exception);
        } catch (RestClientException | JsonProcessingException exception) {
            throw new SlideAiException("Không gọi được slide-ai-service", exception);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new SlideAiException("slide-ai-service trả JSON không hợp lệ", exception);
        }
    }

    private static RestTemplate template(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    public static class SlideAiException extends RuntimeException {
        public SlideAiException(String message) {
            super(message);
        }

        public SlideAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
