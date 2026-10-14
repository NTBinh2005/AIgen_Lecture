package com.example.demo.controller;

import com.example.demo.dto.request.WebhookEventRequest;
import com.example.demo.service.WebhookService;
import com.example.demo.service.serviceImpl.WebhookServiceImpl;
import com.example.demo.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LIVE-BR-05: Webhook endpoint cho video provider.
 * Endpoint công khai (permitAll trong SecurityConfig) —
 * bảo mật dựa hoàn toàn vào HMAC-SHA256 signature.
 *
 * Provider gửi header: X-Webhook-Signature: sha256=<hex>
 */
@RestController
@RequestMapping("/api/webhooks/live")
@Tag(name = "Live Webhooks", description = "LIVE-BR-05: Provider webhook receiver")
public class WebhookController {

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public WebhookController(WebhookServiceImpl webhookService, ObjectMapper objectMapper,
                             Validator validator) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/provider")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "LIVE-BR-05: Receive and process provider webhook event")
    public void handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        // Đọc raw body để verify HMAC — dùng cachedBody từ ContentCachingRequestWrapper
        // hoặc serialize lại từ request object (đơn giản hóa cho MVP)
        WebhookEventRequest request = objectMapper.readValue(rawBody, WebhookEventRequest.class);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new BadRequestException(message);
        }
        webhookService.handleProviderEvent(request, signature, rawBody);
    }
}
