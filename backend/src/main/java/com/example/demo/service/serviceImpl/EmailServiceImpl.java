package com.example.demo.service.serviceImpl;

import com.example.demo.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi email qua Brevo HTTP API (cổng 443) vì các nền tảng free như Render chặn cổng SMTP.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public EmailServiceImpl(
            @Value("${brevo.api-url:https://api.brevo.com/v3/smtp/email}") String apiUrl,
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.sender-email:}") String senderEmail,
            @Value("${brevo.sender-name:EduMind}") String senderName) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().baseUrl(apiUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Async
    @Override
    public void sendEmail(String toEmail, String subject, String body) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }

        if (!isConfigured()) {
            log.info("[Email Service] No BREVO_API_KEY/BREVO_SENDER_EMAIL configured. Mock sending email to {}: [{}] {}",
                    toEmail, subject, body);
            return;
        }

        try {
            send(buildPayload(toEmail, subject, body));
            log.info("[Email Service] Successfully sent email to {}", toEmail);
        } catch (Exception ex) {
            log.error("[Email Service] Failed to send email to {}: {}", toEmail, ex.getMessage());
        }
    }

    @Async
    @Override
    public void sendEmailWithAttachment(
            String toEmail,
            String subject,
            String body,
            String attachmentName,
            byte[] attachment,
            String contentType) {
        if (!StringUtils.hasText(toEmail)) {
            log.warn("[Email Service] Skipped attachment email because recipient is empty");
            return;
        }
        if (attachment == null || attachment.length == 0) {
            log.warn("[Email Service] Skipped attachment email to {} because attachment is empty", toEmail);
            return;
        }
        if (!isConfigured()) {
            log.info("[Email Service] No BREVO_API_KEY/BREVO_SENDER_EMAIL configured. Mock sending attachment {} to {}: [{}] {}",
                    attachmentName, toEmail, subject, body);
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(toEmail, subject, body);
            payload.put("attachment", List.of(Map.of(
                    "name", StringUtils.hasText(attachmentName) ? attachmentName : "attachment",
                    "content", Base64.getEncoder().encodeToString(attachment))));
            send(payload);
            log.info("[Email Service] Successfully sent attachment {} to {}", attachmentName, toEmail);
        } catch (Exception ex) {
            log.error("[Email Service] Failed to send attachment email to {}: {}", toEmail, ex.getMessage(), ex);
        }
    }

    private boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(senderEmail);
    }

    private Map<String, Object> buildPayload(String toEmail, String subject, String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("name", senderName, "email", senderEmail));
        payload.put("to", List.of(Map.of("email", toEmail)));
        payload.put("subject", subject);
        payload.put("textContent", body);
        return payload;
    }

    private void send(Map<String, Object> payload) {
        restClient.post()
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
