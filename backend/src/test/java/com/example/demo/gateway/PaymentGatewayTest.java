package com.example.demo.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayTest {

    private static final String SECRET = "test-sepay-webhook-secret";
    private static final Instant NOW = Instant.parse("2026-10-15T03:00:00Z");
    private SepayGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SepayGateway(new ObjectMapper());
        ReflectionTestUtils.setField(gateway, "bankAccount", "0123456789");
        ReflectionTestUtils.setField(gateway, "bankName", "Vietcombank");
        ReflectionTestUtils.setField(gateway, "webhookAccountNumber", "0123456789");
        ReflectionTestUtils.setField(gateway, "accountHolder", "NGUYEN VAN A");
        ReflectionTestUtils.setField(gateway, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(gateway, "qrBaseUrl", "https://vietqr.app/img");
        ReflectionTestUtils.setField(gateway, "webhookToleranceSeconds", 300L);
        ReflectionTestUtils.setField(gateway, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsVietQrWithExactPaymentData() {
        String url = gateway.createPaymentUrl("DH12345678", new BigDecimal("150000"), null, null, null);
        assertThat(url).startsWith("https://vietqr.app/img?")
                .contains("acc=0123456789", "bank=Vietcombank", "amount=150000",
                        "des=DH12345678", "template=compact", "showinfo=true",
                        "holder=NGUYEN+VAN+A");
    }

    @Test
    void verifiesRawBodySignatureAndRejectsTamperingOrReplay() {
        String body = "{\"id\":92704,\"accountNumber\":\"0123456789\",\"code\":\"DH12345678\","
                + "\"transferType\":\"in\",\"transferAmount\":150000}";
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = "sha256=" + sign(timestamp + "." + body);

        var parsed = gateway.verifyAndParseWebhook(body, signature, timestamp);
        assertThat(parsed.id()).isEqualTo(92704L);
        assertThat(parsed.code()).isEqualTo("DH12345678");
        assertThat(parsed.transferAmount()).isEqualByComparingTo("150000");

        assertThatThrownBy(() -> gateway.verifyAndParseWebhook(body + " ", signature, timestamp))
                .isInstanceOf(SecurityException.class);
        String expired = String.valueOf(NOW.minusSeconds(301).getEpochSecond());
        assertThatThrownBy(() -> gateway.verifyAndParseWebhook(body,
                "sha256=" + sign(expired + "." + body), expired)).isInstanceOf(SecurityException.class);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
