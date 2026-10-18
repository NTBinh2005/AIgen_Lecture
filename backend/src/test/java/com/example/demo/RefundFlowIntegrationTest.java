package com.example.demo;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.PaymentService;
import com.example.demo.service.RefundService;
import com.example.demo.service.SubscriptionService;
import com.example.demo.dto.request.PaymentCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_REFUND_DB_URL:jdbc:h2:mem:refund-flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${TEST_REFUND_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${TEST_REFUND_DB_USER:sa}", "spring.datasource.password=${TEST_REFUND_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.config.import=", "app.video.enabled=false", "gemini.api.key=", "google.oauth.client-id=",
        "spring.mail.username=", "sepay.bank-account=0123456789", "sepay.bank-name=Vietcombank",
        "sepay.webhook-account-number=0123456789", "sepay.refund-source-account=0987654321",
        "sepay.webhook-secret=refund-test-secret", "sepay.qr-base-url=https://vietqr.app/img"
})
@AutoConfigureMockMvc
class RefundFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired SubscriptionPlanRepository plans;
    @Autowired PaymentRepository payments;
    @Autowired RefundRepository refunds;
    @Autowired SubscriptionCreditRepository credits;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired NotificationRepository notifications;
    @Autowired ProcessedWebhookEventRepository processed;
    @Autowired AuditLogRepository audits;
    @Autowired PaymentService paymentService;
    @Autowired RefundService refundService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    User student, admin, stranger;
    Payment payment;

    @BeforeEach void setup() throws Exception {
        student = user(UserRole.STUDENT); admin = user(UserRole.ADMIN); stranger = user(UserRole.STUDENT);
        payment = buy();
    }

    @Test void approvalReturnsQrAndOutgoingWebhookCompletesRefundExactlyOnce() throws Exception {
        JsonNode refund = request();
        int id = refund.path("refundId").asInt();
        assertThat(refund.path("qrUrl").isNull()).isTrue();
        assertThat(refund.path("bankAccount").asText()).isEqualTo("0012345678");
        String token = jwt.generateToken(new UserPrincipal(admin));
        var approved = mvc.perform(patch("/api/refunds/{id}/status", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();
        String qr = mapper.readTree(approved.getResponse().getContentAsString()).path("qrUrl").asText();
        assertThat(qr).contains("acc=0012345678", "bank=Vietcombank", "amount=150000&",
                "des=HT" + id, "holder=NGUYEN+VAN+A");
        assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(credits.findById(payment.getPaymentId()).orElseThrow().isRefunded()).isFalse();
        assertThat(refundNotifications(id)).anySatisfy(n -> assertThat(n.getMessage()).contains("Đã duyệt"));
        mvc.perform(patch("/api/refunds/{id}/status", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
        long event = eventId();
        Map<String, Object> payload = outgoing(event, "HT" + id);
        webhook(payload, false, 200);
        webhook(payload, false, 200);
        Refund completed = refunds.findById(id).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(completed.getSepayWebhookId()).isEqualTo(event);
        assertThat(completed.getSepayReferenceCode()).isEqualTo("BANK-REF-" + event);
        assertThat(completed.getSepayWebhookPayload()).contains("HT" + id, "0987654321");
        assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(credits.findById(payment.getPaymentId()).orElseThrow().isRefunded()).isTrue();
        assertThat(subscriptions.findByUserUserIdOrderByCreatedAtDesc(student.getUserId()).getFirst().getStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(refundNotifications(id).stream().filter(n -> n.getMessage().contains("Đã hoàn tiền"))).hasSize(1);
        assertThat(audits.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(a -> a.getAction() == AuditAction.REFUND_COMPLETED && a.getResourceId().equals("" + id)))
                .hasSize(1);
        mvc.perform(get("/api/refunds/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qrUrl").isEmpty())
                .andExpect(jsonPath("$.sepayWebhookId").value(event));
        // A second distinct bank transfer is not silently treated as a retry.
        webhook(outgoing(eventId(), "HT" + id), false, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "zero", "fraction", "account", "signature", "code", "ambiguous", "partial", "direction"})
    void rejectsInvalidOutgoingTransfersWithoutChangingEntitlements(String invalid) throws Exception {
        int id = request().path("refundId").asInt(); approve(id);
        long event = eventId();
        Map<String, Object> payload = outgoing(event, "HT" + id);
        switch (invalid) {
            case "amount" -> payload.put("transferAmount", 149999);
            case "zero" -> payload.put("transferAmount", 0);
            case "fraction" -> payload.put("transferAmount", 150000.5);
            case "account" -> payload.put("accountNumber", "0123456789");
            case "code" -> { payload.put("code", "DH1234"); payload.put("content", "DH1234"); }
            case "ambiguous" -> payload.put("content", "HT" + id + " HT" + (id + 1));
            case "partial" -> { payload.put("code", "XHT" + id + "Z"); payload.put("content", "XHT" + id + "Z"); }
            case "direction" -> payload.put("transferType", "other");
        }
        webhook(payload, invalid.equals("signature"), invalid.equals("signature") ? 401 : 400);
        assertThat(refunds.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(credits.findById(payment.getPaymentId()).orElseThrow().isRefunded()).isFalse();
        assertThat(processed.existsById("SEPAY:" + event)).isFalse();
        assertThat(refundNotifications(id).stream().filter(n -> n.getMessage().contains("Đã hoàn tiền"))).isEmpty();
    }

    @Test void contentFallbackWorksAndRefundPreservesOtherPurchasedDays() throws Exception {
        buy(); // two paid monthly credits: refund only the first one
        var before = subscriptions.findByUserUserIdOrderByCreatedAtDesc(student.getUserId()).getFirst().getExpiresAt();
        int id = request().path("refundId").asInt(); approve(id);
        var payload = outgoing(eventId(), "HT" + id);
        payload.remove("code"); payload.put("content", "CHUYEN TIEN ht" + id + " hoan hoc phi");
        webhook(payload, false, 200);
        var remaining = subscriptions.findByUserUserIdOrderByCreatedAtDesc(student.getUserId()).getFirst();
        assertThat(remaining.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(java.time.Duration.between(remaining.getExpiresAt(), before).toDays()).isEqualTo(30);
    }

    @Test void requestedAndRejectedRefundsCannotBePaidAndOnlyAdminCanApprove() throws Exception {
        int id = request().path("refundId").asInt();
        webhook(outgoing(eventId(), "HT" + id), false, 400);
        mvc.perform(patch("/api/refunds/{id}/status", id)
                        .header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(student)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        refundService.updateStatus(admin.getUserId(), id, RefundStatus.REJECTED);
        webhook(outgoing(eventId(), "HT" + id), false, 400);
        assertThat(refunds.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.REJECTED);
        mvc.perform(get("/api/refunds/{id}", id)
                        .header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test void accountDetailsAreRequiredAndCannotBeEditedAfterApproval() throws Exception {
        String token = jwt.generateToken(new UserPrincipal(student));
        mvc.perform(post("/api/refunds").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("paymentId", payment.getPaymentId(), "reason", "Hoàn học phí"))))
                .andExpect(status().isBadRequest());
        int id = request().path("refundId").asInt();
        String details = "{\"bankName\":\"BIDV\",\"bankAccount\":\"0098765432\",\"accountHolder\":\"TRAN VAN B\"}";
        mvc.perform(patch("/api/refunds/{id}/bank-account", id)
                        .header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(stranger)))
                        .contentType(MediaType.APPLICATION_JSON).content(details)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/refunds/{id}/bank-account", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(details))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bankAccount").value("0098765432"));
        approve(id);
        mvc.perform(patch("/api/refunds/{id}/bank-account", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(details)).andExpect(status().isBadRequest());
    }

    @Test void legacyMissingRecipientNeedsDetailsAndFreshApproval() throws Exception {
        Refund old = new Refund(); old.setPayment(payment); old.setUser(student);
        old.setAmount(payment.getAmount()); old.setReason("Yêu cầu cũ"); old.setStatus(RefundStatus.APPROVED);
        int id = refunds.save(old).getRefundId();
        webhook(outgoing(eventId(), "HT" + id), false, 400);
        mvc.perform(patch("/api/refunds/{id}/bank-account", id)
                        .header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(student)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"BIDV\",\"bankAccount\":\"0098765432\",\"accountHolder\":\"TRAN VAN B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"));
        approve(id); webhook(outgoing(eventId(), "HT" + id), false, 200);
    }

    @Test void concurrentWebhookRetriesCompleteOnlyOnce() throws Exception {
        int id = request().path("refundId").asInt(); approve(id);
        Map<String, Object> payload = outgoing(eventId(), "HT" + id);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); webhook(payload, false, 200); return null; });
            var second = executor.submit(() -> { start.await(); webhook(payload, false, 200); return null; });
            start.countDown(); first.get(15, java.util.concurrent.TimeUnit.SECONDS); second.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(refundNotifications(id).stream().filter(n -> n.getMessage().contains("Đã hoàn tiền"))).hasSize(1);
        assertThat(credits.findById(payment.getPaymentId()).orElseThrow().isRefunded()).isTrue();
    }

    @Test void concurrentRefundRequestsForOnePaymentCreateOnlyOneRequest() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> create = () -> {
            start.await();
            try {
                refundService.requestRefund(student.getUserId(), new com.example.demo.dto.request.RefundCreateRequest(
                        payment.getPaymentId(), "Hoàn học phí", "BIDV", "0012345678", "NGUYEN VAN A"));
                return true;
            } catch (com.example.demo.common.exception.BadRequestException ex) {
                assertThat(ex.getMessage()).contains("đã có yêu cầu hoàn tiền");
                return false;
            }
        };
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create); var second = executor.submit(create); start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(refunds.findByUserUserId(student.getUserId())).hasSize(1);
    }

    @Test void outgoingCannotUseAnIncomingEventIdOrAnIneligibleOriginalPayment() throws Exception {
        int id = request().path("refundId").asInt(); approve(id);
        long used = eventId();
        var received = new ProcessedWebhookEvent(); received.setEventHash("SEPAY:" + used);
        received.setEventType("SEPAY_PAYMENT_RECEIVED"); processed.save(received);
        webhook(outgoing(used, "HT" + id), false, 400);
        Payment original = payments.findById(payment.getPaymentId()).orElseThrow();
        original.setPaymentMethod(PaymentMethod.VNPAY); payments.save(original);
        webhook(outgoing(eventId(), "HT" + id), false, 400);
        original.setPaymentMethod(PaymentMethod.SEPAY); original.setStatus(PaymentStatus.REFUNDED); payments.save(original);
        webhook(outgoing(eventId(), "HT" + id), false, 400);
        assertThat(refunds.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(credits.findById(payment.getPaymentId()).orElseThrow().isRefunded()).isFalse();
    }

    @Test void persistenceFailureRollsBackRefundEntitlementAndAuditThenAllowsRetry() throws Exception {
        int id = request().path("refundId").asInt(); approve(id);
        long event = eventId();
        Map<String, Object> payload = outgoing(event, "HT" + id);
        // Force a real persistence failure at flush/commit, after business logic has run.
        jdbc.execute("ALTER TABLE processed_webhook_events ADD CONSTRAINT test_refund_rollback CHECK (event_hash <> 'SEPAY:" + event + "')");
        try {
            webhook(payload, false, 409);
            assertThat(refunds.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.APPROVED);
            assertThat(payments.findById(payment.getPaymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(credits.findById(payment.getPaymentId()).orElseThrow().isRefunded()).isFalse();
            assertThat(processed.existsById("SEPAY:" + event)).isFalse();
            assertThat(refundNotifications(id).stream().filter(n -> n.getMessage().contains("Đã hoàn tiền"))).isEmpty();
            assertThat(audits.findTop100ByOrderByCreatedAtDesc().stream()
                    .filter(a -> a.getAction() == AuditAction.REFUND_COMPLETED && a.getResourceId().equals("" + id)))
                    .isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE processed_webhook_events DROP CONSTRAINT test_refund_rollback");
        }
        webhook(payload, false, 200);
        assertThat(refunds.findById(id).orElseThrow().getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    private JsonNode request() throws Exception {
        var result = mvc.perform(post("/api/refunds")
                        .header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(student)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("paymentId", payment.getPaymentId(), "reason", "Hoàn học phí",
                                "bankName", "Vietcombank", "bankAccount", "0012345678", "accountHolder", "NGUYEN VAN A"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private void approve(int id) { refundService.updateStatus(admin.getUserId(), id, RefundStatus.APPROVED); }
    private java.util.List<Notification> refundNotifications(int id) {
        return notifications.findByUserIdOrderByCreatedAtDesc(student.getUserId()).stream()
                .filter(n -> n.getType() == NotificationType.REFUND_STATUS && n.getReferenceId().equals("" + id)).toList();
    }
    private Map<String, Object> outgoing(long event, String code) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("id", event); payload.put("code", code); payload.put("content", code + " hoan tien");
        payload.put("accountNumber", "0987654321"); payload.put("transferType", "out");
        payload.put("transferAmount", 150000); payload.put("referenceCode", "BANK-REF-" + event);
        return payload;
    }
    private void webhook(Map<String, Object> payload, boolean tamper, int expectedStatus) throws Exception {
        String body = mapper.writeValueAsString(payload);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("refund-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        var result = mvc.perform(post("/api/payments/webhook/sepay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-SePay-Timestamp", timestamp).header("X-SePay-Signature", signature)
                        .content(tamper ? body + " " : body)).andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) result.andExpect(jsonPath("$.success").value(true));
    }
    private long eventId() { return java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE); }
    private Payment buy() throws Exception {
        var plan = plans.findByCodeIgnoreCase("STUDENT_MONTHLY").orElseThrow();
        var response = paymentService.createPayment(student.getUserId(), new PaymentCreateRequest(plan.getPlanId(), new BigDecimal("150000")));
        webhook(Map.of("id", eventId(), "code", response.transactionId(), "transferType", "in",
                "accountNumber", "0123456789", "transferAmount", 150000), false, 200);
        return payments.findById(response.paymentId()).orElseThrow();
    }
    private User user(UserRole role) {
        User user = new User(); user.setName("Refund test"); user.setRole(role); user.setStatus(UserStatus.ACTIVE);
        user.setEmail(UUID.randomUUID() + "@example.com"); user.setPasswordHash("unused"); return users.save(user);
    }
}
