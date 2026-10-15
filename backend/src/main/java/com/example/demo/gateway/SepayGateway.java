package com.example.demo.gateway;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.dto.request.SepayWebhookRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tích hợp QR chuyển khoản và webhook HMAC-SHA256 của SePay. */
@Component("SEPAY")
@RequiredArgsConstructor
public class SepayGateway implements PaymentGateway {

    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

    @Value("${sepay.bank-account:}")
    private String bankAccount;

    @Value("${sepay.bank-name:}")
    private String bankName;

    /** Có thể khác bankAccount khi QR sử dụng virtual account (VA). */
    @Value("${sepay.webhook-account-number:}")
    private String webhookAccountNumber;

    @Value("${sepay.account-holder:}")
    private String accountHolder;

    @Value("${sepay.webhook-secret:}")
    private String webhookSecret;

    @Value("${sepay.qr-base-url:https://vietqr.app/img}")
    private String qrBaseUrl;

    @Value("${sepay.webhook-tolerance-seconds:300}")
    private long webhookToleranceSeconds;

    @Override
    public String createPaymentUrl(String transactionId, BigDecimal amount, String orderInfo,
                                   String returnUrl, String ipAddress) {
        if (bankAccount == null || bankAccount.isBlank() || bankName == null || bankName.isBlank()) {
            throw new IllegalStateException("Cổng SePay chưa được cấu hình (thiếu SEPAY_BANK_ACCOUNT / SEPAY_BANK_NAME)");
        }
        long amountVnd;
        try {
            amountVnd = amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Số tiền SePay phải là số nguyên VND", ex);
        }

        StringBuilder url = new StringBuilder(qrBaseUrl)
                .append("?acc=").append(encode(bankAccount))
                .append("&bank=").append(encode(bankName))
                .append("&amount=").append(amountVnd)
                .append("&des=").append(encode(transactionId))
                .append("&template=compact&showinfo=true");
        if (accountHolder != null && !accountHolder.isBlank()) {
            url.append("&holder=").append(encode(accountHolder));
        }
        return url.toString();
    }

    public SepayWebhookRequest verifyAndParseWebhook(String rawBody, String signature, String timestamp) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new SecurityException("Webhook SePay chưa được cấu hình secret");
        }
        if (rawBody == null || rawBody.isBlank() || signature == null || timestamp == null) {
            throw new SecurityException("Thiếu dữ liệu xác thực webhook SePay");
        }

        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new SecurityException("Timestamp webhook SePay không hợp lệ");
        }
        long now = Instant.now(clock).getEpochSecond();
        if (Math.abs(now - signedAt) > webhookToleranceSeconds) {
            throw new SecurityException("Webhook SePay đã hết hạn");
        }

        String expected = "sha256=" + hmacSha256(webhookSecret, timestamp + "." + rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Chữ ký webhook SePay không hợp lệ");
        }
        try {
            return objectMapper.readValue(rawBody, SepayWebhookRequest.class);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Payload webhook SePay không hợp lệ");
        }
    }

    public boolean matchesConfiguredAccount(String webhookAccount) {
        String expectedAccount = webhookAccountNumber == null || webhookAccountNumber.isBlank()
                ? bankAccount : webhookAccountNumber;
        return expectedAccount != null && webhookAccount != null
                && MessageDigest.isEqual(expectedAccount.trim().getBytes(StandardCharsets.UTF_8),
                webhookAccount.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể xác thực webhook SePay", ex);
        }
    }
}
