package com.example.demo.service;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.dto.response.PresentationVersionResponse;
import com.example.demo.service.serviceImpl.SlideAiClient;
import com.example.demo.service.serviceImpl.SlideAiDeckMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dựng HTML trình chiếu cho một phiên bản slide bằng slide-ai-service (không gọi AI).
 * Mỗi lần dựng thành công được lưu lại; khi service tắt thì trả bản đã lưu của đúng nội dung đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationRenderService {
    private final PresentationService presentationService;
    private final SlideAiClient slideAiClient;
    private final PresentationRenderCacheStore cacheStore;
    private final ObjectMapper objectMapper;

    public String render(UUID presentationId, UUID versionId, Integer requesterId, boolean admin) {
        // get()/versions() đã kiểm tra quyền xem của người gọi.
        PresentationResponse presentation = presentationService.get(presentationId, requesterId, admin);
        PresentationVersionResponse version = versionId == null
                ? presentation.currentVersion()
                : presentationService.versions(presentationId, requesterId, admin).stream()
                        .filter(v -> v.versionId().equals(versionId))
                        .findFirst()
                        .orElse(null);
        if (version == null) {
            throw new ResourceNotFoundException("Không tìm thấy phiên bản slide để trình chiếu");
        }
        return renderVersion(version, presentation.templateId());
    }

    /** Người gọi phải tự kiểm tra quyền xem phiên bản này. */
    public String renderVersion(PresentationVersionResponse version, String templateId) {
        String theme = SlideAiDeckMapper.themeFor(templateId);
        ObjectNode deck = SlideAiDeckMapper.toDeck(version.title(), version.slides(), theme);
        String key = cacheKey(deck, theme);
        try {
            String html = slideAiClient.render(deck, theme);
            saveQuietly(key, version.versionId(), theme, html);
            return html;
        } catch (SlideAiClient.SlideAiException exception) {
            Optional<String> cached = cacheStore.find(key);
            if (cached.isEmpty()) {
                throw exception;
            }
            log.info("slide-ai-service unavailable ({}); serving cached render of version {}",
                    exception.getMessage(), version.versionId());
            return cached.get();
        }
    }

    private void saveQuietly(String key, UUID versionId, String theme, String html) {
        try {
            cacheStore.save(key, versionId, theme, html);
        } catch (RuntimeException exception) {
            // Hai request cùng ghi một khoá hoặc DB bận: lần sau sẽ ghi lại, không làm hỏng request.
            log.debug("Skip presentation render cache write for version {}: {}", versionId, exception.getMessage());
        }
    }

    private String cacheKey(ObjectNode deck, String theme) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (theme + "\n" + objectMapper.writeValueAsString(deck)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot compute presentation render cache key", exception);
        }
    }
}
