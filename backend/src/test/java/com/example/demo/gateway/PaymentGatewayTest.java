package com.example.demo.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentGatewayTest {

    private VnpayGateway vnpayGateway;
    private MomoGateway momoGateway;

    @BeforeEach
    void setUp() {
        vnpayGateway = new VnpayGateway();
        ReflectionTestUtils.setField(vnpayGateway, "tmnCode", "TEST_TMN");
        ReflectionTestUtils.setField(vnpayGateway, "hashSecret", "test-secret-key-12345");
        ReflectionTestUtils.setField(vnpayGateway, "vnpayPaymentUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(vnpayGateway, "defaultReturnUrl", "http://localhost:8080/callback/vnpay");

        momoGateway = new MomoGateway(new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(momoGateway, "partnerCode", "MOMOTEST");
        ReflectionTestUtils.setField(momoGateway, "accessKey", "MOMO_ACCESS_KEY");
        ReflectionTestUtils.setField(momoGateway, "secretKey", "MOMO_SECRET_KEY");
        ReflectionTestUtils.setField(momoGateway, "momoPaymentUrl", "https://test-payment.momo.vn/v2/gateway/api/create");
        ReflectionTestUtils.setField(momoGateway, "defaultReturnUrl", "http://localhost:8080/callback/momo");
        ReflectionTestUtils.setField(momoGateway, "notifyUrl", "http://localhost:8080/webhook/momo");
    }

    @Test
    void testVnpay_CreatePaymentUrl() {
        String txnId = "TXN-123456";
        BigDecimal amount = new BigDecimal("100000");

        String url = vnpayGateway.createPaymentUrl(
                txnId, amount, "Test payment order", "http://return.url", "127.0.0.1"
        );

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_TmnCode=TEST_TMN");
        // VNPay amount x100 = 10000000
        assertThat(url).contains("vnp_Amount=10000000");
        assertThat(url).contains("vnp_TxnRef=TXN-123456");
        assertThat(url).contains("vnp_SecureHash=");
    }

    @Test
    void testVnpay_VerifyCallback_SuccessAndFailure() {
        String txnId = "TXN-789";
        BigDecimal amount = new BigDecimal("200000");

        // Sinh url để lấy đúng SecureHash hợp lệ
        String url = vnpayGateway.createPaymentUrl(
                txnId, amount, "Order 789", "http://return.url", "127.0.0.1"
        );

        // Parse query params từ url
        String query = url.substring(url.indexOf('?') + 1);
        Map<String, String> callbackParams = new HashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                callbackParams.put(key, value);
            }
        }

        // Test 1: Signature đúng
        assertThat(vnpayGateway.verifyCallback(callbackParams)).isTrue();
        assertThat(vnpayGateway.extractTransactionId(callbackParams)).isEqualTo(txnId);

        // Test 2: Status code
        callbackParams.put("vnp_ResponseCode", "00");
        assertThat(vnpayGateway.extractStatus(callbackParams)).isEqualTo("SUCCESS");

        callbackParams.put("vnp_ResponseCode", "24");
        assertThat(vnpayGateway.extractStatus(callbackParams)).isEqualTo("FAILED");

        // Test 3: Bị giả mạo tham số (amount thay đổi)
        Map<String, String> tamperedParams = new HashMap<>(callbackParams);
        tamperedParams.put("vnp_Amount", "99999999");
        assertThat(vnpayGateway.verifyCallback(tamperedParams)).isFalse();
    }

    @Test
    void testMomo_VerifyCallback_Validation() {
        Map<String, String> momoParams = new HashMap<>();
        momoParams.put("partnerCode", "MOMOTEST");
        momoParams.put("orderId", "MOMO-ORDER-001");
        momoParams.put("requestId", "REQ-001");
        momoParams.put("amount", "50000");
        momoParams.put("orderInfo", "Test Momo");
        momoParams.put("orderType", "momo_wallet");
        momoParams.put("transId", "12345678");
        momoParams.put("resultCode", "0");
        momoParams.put("message", "Successful.");
        momoParams.put("payType", "qr");
        momoParams.put("responseTime", "1620000000");
        momoParams.put("extraData", "");

        // Callback thiếu chữ ký
        assertThat(momoGateway.verifyCallback(momoParams)).isFalse();

        // Chữ ký sai
        momoParams.put("signature", "invalid-signature");
        assertThat(momoGateway.verifyCallback(momoParams)).isFalse();

        // Extract helpers
        assertThat(momoGateway.extractTransactionId(momoParams)).isEqualTo("MOMO-ORDER-001");
        assertThat(momoGateway.extractStatus(momoParams)).isEqualTo("SUCCESS");

        momoParams.put("resultCode", "1006"); // user cancelled
        assertThat(momoGateway.extractStatus(momoParams)).isEqualTo("FAILED");
    }
}
