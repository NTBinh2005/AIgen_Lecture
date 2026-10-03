package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Kiểm tra các secret nhạy cảm lúc khởi động.
 *
 * <p>Ở production (đặt {@code APP_ENFORCE_SECRETS=true}) ứng dụng sẽ từ chối khởi động nếu
 * secret vẫn để giá trị mặc định trong code hoặc để rỗng — buộc phải cấp qua biến môi trường.
 * Ở môi trường dev (mặc định) chỉ ghi cảnh báo để không cản trở việc chạy local.
 */
@Component
public class SecretsValidator {
    private static final Logger log = LoggerFactory.getLogger(SecretsValidator.class);

    /** Các giá trị mặc định không an toàn — không được dùng khi deploy. */
    private static final String DEFAULT_JWT_SECRET =
            "edumind-super-secret-key-for-jwt-signing-must-be-at-least-512-bits-long-2026";
    private static final String DEFAULT_WEBHOOK_SECRET = "change-me-in-production";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${live.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.security.enforce-secrets:false}")
    private boolean enforceSecrets;

    @PostConstruct
    void validate() {
        Map<String, String> issues = new LinkedHashMap<>();
        if (!StringUtils.hasText(jwtSecret) || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            issues.put("jwt.secret", "JWT_SECRET");
        }
        if (!StringUtils.hasText(webhookSecret) || DEFAULT_WEBHOOK_SECRET.equals(webhookSecret)) {
            issues.put("live.webhook.secret", "LIVE_WEBHOOK_SECRET");
        }

        if (issues.isEmpty()) {
            return;
        }

        if (enforceSecrets) {
            String names = String.join(", ", issues.values());
            throw new IllegalStateException(
                    "Secret chưa được cấu hình an toàn khi APP_ENFORCE_SECRETS=true. "
                            + "Hãy đặt các biến môi trường: " + names);
        }
        issues.forEach((property, env) -> log.warn(
                "[BẢO MẬT] {} đang dùng giá trị mặc định không an toàn. "
                        + "Hãy đặt {} khi deploy (bật APP_ENFORCE_SECRETS=true để bắt buộc).",
                property, env));
    }
}
