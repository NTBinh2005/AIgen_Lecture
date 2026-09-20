package com.example.demo.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Tích hợp cổng thanh toán <strong>VNPay</strong>.
 * <p>
 * Tài liệu API: <a href="https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/">
 * VNPay Payment API</a>
 * <p>
 * Luồng hoạt động:
 * <ol>
 *   <li>Client gọi {@code POST /api/payments} với {@code paymentMethod=VNPAY}</li>
 *   <li>Backend sinh URL VNPay và trả về client (field {@code paymentUrl})</li>
 *   <li>Client redirect người dùng tới URL VNPay</li>
 *   <li>Sau khi thanh toán, VNPay redirect về {@code vnpay.return-url}
 *       kèm query params (đây là Return URL — chỉ cho UX, không cập nhật trạng thái)</li>
 *   <li>VNPay gọi IPN tới {@code POST /api/payments/webhook/vnpay} để xác nhận giao dịch</li>
 *   <li>Backend xác thực HMAC-SHA512, cập nhật trạng thái Payment, phát event</li>
 * </ol>
 */
@Slf4j
@Component("VNPAY")
public class VnpayGateway implements PaymentGateway {

    private static final String VNP_VERSION = "2.1.0";
    private static final String VNP_COMMAND = "pay";
    private static final String VNP_CURR_CODE = "VND";
    private static final String VNP_LOCALE = "vn";
    private static final String VNP_ORDER_TYPE = "other";

    @Value("${vnpay.tmn-code:DEMO0001}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:vnpay-secret-key-for-dev-testing-only}")
    private String hashSecret;

    @Value("${vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpayPaymentUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/payments/callback/vnpay}")
    private String defaultReturnUrl;

    @Override
    public String createPaymentUrl(String transactionId, BigDecimal amount, String orderInfo,
                                   String returnUrl, String ipAddress) {
        String createDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        // VNPay nhận số tiền x100 (không dấu thập phân), tính bằng đồng
        long vnpAmount = amount.multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", VNP_VERSION);
        vnpParams.put("vnp_Command", VNP_COMMAND);
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", VNP_CURR_CODE);
        vnpParams.put("vnp_TxnRef", transactionId);
        vnpParams.put("vnp_OrderInfo", orderInfo != null ? orderInfo : "Thanh toan don hang " + transactionId);
        vnpParams.put("vnp_OrderType", VNP_ORDER_TYPE);
        vnpParams.put("vnp_Locale", VNP_LOCALE);
        vnpParams.put("vnp_ReturnUrl", returnUrl != null ? returnUrl : defaultReturnUrl);
        vnpParams.put("vnp_IpAddr", ipAddress != null ? ipAddress : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", createDate);

        // Sinh query string (đã sort theo key để ký HMAC)
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : vnpParams.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                hashData.append(entry.getKey()).append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                hashData.append('&');
                query.append('&');
            }
        }
        // Xóa dấu & cuối
        if (!hashData.isEmpty()) hashData.deleteCharAt(hashData.length() - 1);
        if (!query.isEmpty()) query.deleteCharAt(query.length() - 1);

        String secureHash = hmacSHA512(hashSecret, hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        String paymentUrl = vnpayPaymentUrl + "?" + query;
        log.debug("[VNPay] Generated payment URL for txn={}", transactionId);
        return paymentUrl;
    }

    public String computeSignature(Map<String, String> params) {
        Map<String, String> sortedParams = new TreeMap<>(params);
        sortedParams.remove("vnp_SecureHash");
        sortedParams.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                hashData.append(entry.getKey()).append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                        .append('&');
            }
        }
        if (!hashData.isEmpty()) hashData.deleteCharAt(hashData.length() - 1);

        return hmacSHA512(hashSecret, hashData.toString());
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            log.warn("[VNPay] Missing vnp_SecureHash in callback");
            return false;
        }

        String computedHash = computeSignature(params);
        boolean valid = computedHash.equalsIgnoreCase(receivedHash);
        if (!valid) {
            log.warn("[VNPay] Signature mismatch: computed={}, received={}", computedHash, receivedHash);
        }
        return valid;
    }

    @Override
    public String extractStatus(Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        return "00".equals(responseCode) ? "SUCCESS" : "FAILED";
    }

    @Override
    public String extractTransactionId(Map<String, String> params) {
        return params.get("vnp_TxnRef");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("[VNPay] Error computing HMAC-SHA512", e);
        }
    }
}
