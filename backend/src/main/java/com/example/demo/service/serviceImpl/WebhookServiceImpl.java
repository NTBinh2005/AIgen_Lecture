package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.WebhookEventRequest;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.RecordingStatus;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.ProcessedWebhookEventRepository;
import com.example.demo.entity.ProcessedWebhookEvent;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.RecordingService;
import com.example.demo.service.WebhookService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LIVE-BR-05: Xử lý webhook từ video provider.
 * - Xác thực HMAC-SHA256 (header X-Webhook-Signature vs raw body).
 * - Route theo eventType.
 * - Xử lý idempotent cho tất cả sự kiện.
 *
 * Exception ngoại lệ:
 * - Webhook đến trễ không chuyển session ENDED → LIVE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final LiveSessionRepository liveSessionRepository;
    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final RecordingService recordingService;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Value("${live.webhook.secret:change-me-in-production}")
    private String webhookSecret;

    private static final String HMAC_ALGO = "HmacSHA256";

    // ─────────────────────────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void handleProviderEvent(WebhookEventRequest request, String signature, String rawBody) {
        // LIVE-BR-05: xác thực HMAC trước khi xử lý
        verifySignature(rawBody, signature);

        String eventHash = sha256(rawBody);
        if (processedWebhookEventRepository.existsById(eventHash)) {
            log.info("[WEBHOOK] Duplicate event ignored: {}", eventHash);
            return;
        }
        ProcessedWebhookEvent processed = new ProcessedWebhookEvent();
        processed.setEventHash(eventHash);
        processed.setEventType(request.eventType());
        processedWebhookEventRepository.save(processed);

        log.info("[WEBHOOK] event={} meetingId={}", request.eventType(), request.externalMeetingId());

        switch (request.eventType()) {
            case "meeting.started"    -> handleMeetingStarted(request);
            case "meeting.ended"      -> handleMeetingEnded(request);
            case "participant.joined" -> handleParticipantJoined(request);
            case "participant.left"   -> handleParticipantLeft(request);
            case "recording.ready"    -> handleRecordingReady(request);
            case "recording.failed"   -> handleRecordingFailed(request);
            default -> log.warn("[WEBHOOK] Unknown eventType={}", request.eventType());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    private void handleMeetingStarted(WebhookEventRequest request) {
        findSession(request.externalMeetingId()).ifPresent(session -> {
            // Idempotent: chỉ update nếu chưa LIVE
            if (session.getStatus() == LiveSessionStatus.OPEN
                    || session.getStatus() == LiveSessionStatus.SCHEDULED) {
                session.setStatus(LiveSessionStatus.LIVE);
                liveSessionRepository.save(session);
                log.info("[WEBHOOK] session={} → LIVE", session.getSessionId());
            }
        });
    }

    private void handleMeetingEnded(WebhookEventRequest request) {
        findSession(request.externalMeetingId()).ifPresent(session -> {
            // Exception: Webhook đến trễ không được chuyển ENDED → LIVE
            if (session.getStatus() == LiveSessionStatus.ENDED
                    || session.getStatus() == LiveSessionStatus.CANCELLED) {
                log.info("[WEBHOOK] Ignoring late meeting.ended for session={}", session.getSessionId());
                return;
            }
            session.setStatus(LiveSessionStatus.ENDED);
            liveSessionRepository.save(session);
            log.info("[WEBHOOK] session={} → ENDED", session.getSessionId());
        });
    }

    private void handleParticipantJoined(WebhookEventRequest request) {
        findSession(request.externalMeetingId()).ifPresent(session -> {
            Integer userId = resolveUserId(request.payload());
            attendanceService.recordOnlineJoin(
                    session.getSessionId(),
                    request.externalParticipantId(),
                    userId);
        });
    }

    private void handleParticipantLeft(WebhookEventRequest request) {
        findSession(request.externalMeetingId()).ifPresent(session ->
                attendanceService.recordOnlineLeave(
                        session.getSessionId(),
                        request.externalParticipantId())
        );
    }

    private void handleRecordingReady(WebhookEventRequest request) {
        Map<String, Object> payload = request.payload();
        String playbackUrl    = payload != null ? (String) payload.get("playbackUrl") : null;
        Long   durationSecs   = payload != null ? toLong(payload.get("durationSeconds")) : null;
        Long   fileSizeBytes  = payload != null ? toLong(payload.get("fileSizeBytes")) : null;

        recordingService.handleWebhookUpdate(
                request.externalRecordingId(),
                RecordingStatus.READY,
                playbackUrl,
                durationSecs,
                fileSizeBytes);
    }

    private void handleRecordingFailed(WebhookEventRequest request) {
        recordingService.handleWebhookUpdate(
                request.externalRecordingId(),
                RecordingStatus.FAILED,
                null, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LIVE-BR-05: Xác thực HMAC-SHA256.
     * Signature header dạng: "sha256=<hex>"
     */
    private void verifySignature(String rawBody, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Missing or invalid webhook signature");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedHex = "sha256=" + HexFormat.of().formatHex(expected);
            if (!MessageDigest.isEqual(
                    expectedHex.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                    signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Webhook signature mismatch");
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Webhook signature verification error", e);
        }
    }

    private Optional<LiveSession> findSession(String externalMeetingId) {
        if (externalMeetingId == null) return Optional.empty();
        var opt = liveSessionRepository.findByExternalMeetingId(externalMeetingId);
        if (opt.isEmpty()) {
            log.warn("[WEBHOOK] No session found for externalMeetingId={}", externalMeetingId);
        }
        return opt;
    }

    /**
     * Resolve userId từ payload webhook (key: "userId" hoặc "user_id").
     * Trả về null nếu không tìm thấy (guest).
     */
    private Integer resolveUserId(Map<String, Object> payload) {
        if (payload == null) return null;
        Object raw = payload.getOrDefault("userId", payload.get("user_id"));
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
