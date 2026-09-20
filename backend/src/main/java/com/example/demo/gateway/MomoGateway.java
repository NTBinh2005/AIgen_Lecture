package com.example.demo.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tích hợp cổng thanh toán <strong>MoMo</strong> (phiên bản API v2 — redirect).
 * <p>
 * Tài liệu API: <a href="https://developers.momo.vn/v3/docs/payment/api/payment-api">
 * MoMo Payment API</a>
 * <p>
 * Luồng hoạt động:
 * <ol>
 *   <li>Client gọi {@code POST /api/payments} với {@code paymentMethod=MOMO}</li>
 *   <li>Backend gọi MoMo API để lấy {@code payUrl} và trả về client</li>
 *   <li>Client redirect người dùng tới {@code payUrl}</li>
 *   <li>Sau khi thanh toán, MoMo redirect về {@code returnUrl} (chỉ cho UX)</li>
 *   <li>MoMo gọi IPN/Notify tới {@code POST /api/payments/webhook/momo}</li>
 *   <li>Backend xác thực HMAC-SHA256, cập nhật trạng thái Payment, phát event</li>
 * </ol>
 */
@Slf4j
@Component("MOMO")
@RequiredArgsConstructor
public class MomoGateway implements PaymentGateway {

    private static final String MOMO_REQUEST_TYPE = "payWithMethod";

    @Value("${momo.partner-code:MOMOTEST}")
    private String partnerCode;

    @Value("${momo.access-key:F8BBA842ECF85}")
    private String accessKey;

    @Value("${momo.secret-key:K951B6PE1waDMi640xX08PD3vg6EkVlz}")
    private String secretKey;

    @Value("${momo.payment-url:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String momoPaymentUrl;

    @Value("${momo.return-url:http://localhost:8080/api/payments/callback/momo}")
    private String defaultReturnUrl;

    @Value("${momo.notify-url:http://localhost:8080/api/payments/webhook/momo}")
    private String notifyUrl;

    private final ObjectMapper objectMapper;
    private final org.springframework.web.client.RestClient restClient =
            org.springframework.web.client.RestClient.create();

    @Override
    public String createPaymentUrl(String transactionId, BigDecimal amount, String orderInfo,
                                   String returnUrl, String ipAddress) {
        try {
            String requestId = UUID.randomUUID().toString();
            long momoAmount = amount.longValue();
            String effectiveReturnUrl = returnUrl != null ? returnUrl : defaultReturnUrl;
            String effectiveOrderInfo = orderInfo != null ? orderInfo : "Thanh toan don hang " + transactionId;
            String extraData = "";
            String requestTime = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Chuỗi ký: sắp xếp theo tên tham số theo đặc tả MoMo
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + momoAmount +
                    "&extraData=" + extraData +
                    "&ipnUrl=" + notifyUrl +
                    "&orderId=" + transactionId +
                    "&orderInfo=" + effectiveOrderInfo +
                    "&partnerCode=" + partnerCode +
                    "&redirectUrl=" + effectiveReturnUrl +
                    "&requestId=" + requestId +
                    "&requestType=" + MOMO_REQUEST_TYPE;

            String signature = hmacSHA256(secretKey, rawSignature);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("partnerCode", partnerCode);
            requestBody.put("accessKey", accessKey);
            requestBody.put("requestId", requestId);
            requestBody.put("amount", momoAmount);
            requestBody.put("orderId", transactionId);
            requestBody.put("orderInfo", effectiveOrderInfo);
            requestBody.put("redirectUrl", effectiveReturnUrl);
            requestBody.put("ipnUrl", notifyUrl);
            requestBody.put("extraData", extraData);
            requestBody.put("requestType", MOMO_REQUEST_TYPE);
            requestBody.put("signature", signature);
            requestBody.put("lang", "vi");

            log.debug("[MoMo] Calling MoMo API for txn={}", transactionId);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(momoPaymentUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new RuntimeException("[MoMo] Empty response from MoMo API");
            }

            Object payUrl = response.get("payUrl");
            if (payUrl == null) {
                log.error("[MoMo] No payUrl in response: {}", response);
                throw new RuntimeException("[MoMo] No payUrl returned. resultCode=" + response.get("resultCode")
                        + ", message=" + response.get("message"));
            }

            log.info("[MoMo] Payment URL generated for txn={}", transactionId);
            return payUrl.toString();

        } catch (Exception e) {
            log.error("[MoMo] Error creating payment URL for txn={}: {}", transactionId, e.getMessage());
            throw new RuntimeException("Không thể kết nối cổng thanh toán MoMo: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        String receivedSignature = params.get("signature");
        if (receivedSignature == null || receivedSignature.isBlank()) {
            log.warn("[MoMo] Missing signature in callback");
            return false;
        }

        // Xây dựng chuỗi ký theo đặc tả MoMo IPN
        String rawSignature = "accessKey=" + accessKey +
                "&amount=" + params.getOrDefault("amount", "") +
                "&extraData=" + params.getOrDefault("extraData", "") +
                "&message=" + params.getOrDefault("message", "") +
                "&orderId=" + params.getOrDefault("orderId", "") +
                "&orderInfo=" + params.getOrDefault("orderInfo", "") +
                "&orderType=" + params.getOrDefault("orderType", "") +
                "&partnerCode=" + params.getOrDefault("partnerCode", "") +
                "&payType=" + params.getOrDefault("payType", "") +
                "&requestId=" + params.getOrDefault("requestId", "") +
                "&responseTime=" + params.getOrDefault("responseTime", "") +
                "&resultCode=" + params.getOrDefault("resultCode", "") +
                "&transId=" + params.getOrDefault("transId", "");

        String computedSignature = hmacSHA256(secretKey, rawSignature);
        boolean valid = computedSignature.equalsIgnoreCase(receivedSignature);
        if (!valid) {
            log.warn("[MoMo] Signature mismatch for orderId={}", params.get("orderId"));
        }
        return valid;
    }

    @Override
    public String extractStatus(Map<String, String> params) {
        // MoMo: resultCode=0 là thành công
        String resultCode = params.get("resultCode");
        return "0".equals(resultCode) ? "SUCCESS" : "FAILED";
    }

    @Override
    public String extractTransactionId(Map<String, String> params) {
        // orderId trong callback MoMo chính là transactionId nội bộ
        return params.get("orderId");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String hmacSHA256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("[MoMo] Error computing HMAC-SHA256", e);
        }
    }
}
