package com.example.demo.service;

import com.example.demo.dto.request.WebhookEventRequest;

/**
 * LIVE-BR-05: Nhận và xử lý webhook từ video provider.
 * Xác thực HMAC-SHA256, route theo eventType, xử lý idempotent.
 */
public interface WebhookService {

    /**
     * Xử lý sự kiện webhook từ provider.
     *
     * @param request   payload đã parse
     * @param signature giá trị header X-Webhook-Signature
     * @param rawBody   raw request body dùng để verify HMAC
     */
    void handleProviderEvent(WebhookEventRequest request, String signature, String rawBody);
}
