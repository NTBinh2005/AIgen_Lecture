package com.example.demo;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.request.PaymentWebhookRequest;
import com.example.demo.dto.request.TokenVerifyRequest;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.subscriptions.enforce-premium=false",
        "spring.datasource.url=jdbc:h2:mem:integration-flow-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "google.oauth.client-id=fake-google-client-id",
        "sepay.bank-account=0123456789",
        "sepay.bank-name=Vietcombank",
        "sepay.webhook-secret=test-sepay-webhook-secret"
})
@AutoConfigureMockMvc
public class IntegrationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private LearningAccessRepository learningAccessRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired private com.example.demo.repository.SubscriptionCreditRepository creditRepository;
    @Autowired private ProcessedWebhookEventRepository processedWebhookEventRepository;

    private User student;
    private SubscriptionPlan studentPlan;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        learningAccessRepository.deleteAll();
        invoiceRepository.deleteAll();
        creditRepository.deleteAll();
        subscriptionRepository.deleteAll();
        processedWebhookEventRepository.deleteAll();
        paymentRepository.deleteAll();
        userRepository.deleteAll();

        student = new User();
        student.setName("Test Student");
        student.setEmail("student@test.com");
        student.setPasswordHash(passwordEncoder.encode("password123"));
        student.setRole(UserRole.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        student.setAuthProvider(AuthProvider.LOCAL);
        student = userRepository.save(student);
        studentPlan = subscriptionPlanRepository.findByCodeIgnoreCase("STUDENT_MONTHLY").orElseThrow();
    }

    @Test
    void testVerifyTokenEndpoint_ValidAndInvalid() throws Exception {
        // Given valid token
        UserPrincipal principal = new UserPrincipal(student);
        String validToken = jwtTokenProvider.generateToken(principal);

        // 1. Verify valid token
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenVerifyRequest(validToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userId").value(student.getUserId()))
                .andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.permissions").isArray());

        // 2. Verify invalid token
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenVerifyRequest("invalid-token-xyz"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void testPaymentWebhook_FullFlowAndIdempotency() {
        // 1. Create Payment
        PaymentResponse paymentResp = paymentService.createPayment(
                student.getUserId(),
                new PaymentCreateRequest(studentPlan.getPlanId(), new BigDecimal("1.00"))
        );

        assertThat(paymentResp.status()).isEqualTo(PaymentStatus.PENDING);
        String txId = paymentResp.transactionId();

        // 2. First Webhook: SUCCESS
        PaymentWebhookRequest webhookReq = new PaymentWebhookRequest(
                txId,
                "SUCCESS",
                new BigDecimal("150000.00")
        );
        paymentService.handleWebhook(webhookReq);

        // Verify Payment updated
        Payment paid = paymentRepository.findByTransactionId(txId).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Verify Invoice created
        var invoices = invoiceRepository.findByUserUserId(student.getUserId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getAmount()).isEqualByComparingTo("150000.00");
        assertThat(invoices.get(0).getInvoiceNumber()).startsWith("INV-");

        // Verify Learning Access granted
        var accesses = learningAccessRepository.findByUserUserId(student.getUserId());
        assertThat(accesses).hasSize(1);
        assertThat(accesses.get(0).getProductId()).isEqualTo(studentPlan.getPlanId());
        assertThat(accesses.get(0).getAccessType()).isEqualTo("STUDENT_MONTHLY");
        assertThat(subscriptionRepository.findByUserUserIdOrderByCreatedAtDesc(student.getUserId())).hasSize(1);

        // Verify Notifications generated
        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(student.getUserId());
        assertThat(notifications).isNotEmpty();

        // 3. Second Webhook (Duplicate/Idempotency check)
        paymentService.handleWebhook(webhookReq);

        // Ensure no duplicate invoices or learning accesses
        assertThat(invoiceRepository.findByUserUserId(student.getUserId())).hasSize(1);
        assertThat(learningAccessRepository.findByUserUserId(student.getUserId())).hasSize(1);
    }

    @Test
    void testInterServiceCheckUserAccessEndpoint() throws Exception {
        String studentToken = jwtTokenProvider.generateToken(new UserPrincipal(student));
        mockMvc.perform(get("/api/access/check-user").header("Authorization", "Bearer " + studentToken)
                .param("userId", student.getUserId().toString()).param("productId", "500"))
                .andExpect(status().isForbidden());
        User admin = new User(); admin.setName("Admin"); admin.setEmail("admin-check@test.com");
        admin.setPasswordHash(passwordEncoder.encode("password123")); admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE); admin = userRepository.save(admin);
        String token = jwtTokenProvider.generateToken(new UserPrincipal(admin));
        // Before grant
        mockMvc.perform(get("/api/access/check-user")
                        .header("Authorization", "Bearer " + token)
                        .param("userId", student.getUserId().toString())
                        .param("productId", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getUserId()))
                .andExpect(jsonPath("$.productId").value(500))
                .andExpect(jsonPath("$.hasAccess").value(false));

        // Grant access
        LearningAccess access = new LearningAccess();
        access.setUser(student);
        access.setProductId(500);
        access.setPaymentId(12345);
        access.setAccessType("PREMIUM");
        learningAccessRepository.save(access);

        // After grant
        mockMvc.perform(get("/api/access/check-user")
                        .header("Authorization", "Bearer " + token)
                        .param("userId", student.getUserId().toString())
                        .param("productId", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getUserId()))
                .andExpect(jsonPath("$.productId").value(500))
                .andExpect(jsonPath("$.hasAccess").value(true));
    }

    @Test
    void testSepayPaymentCreationAndSignedWebhook() throws Exception {
        UserPrincipal principal = new UserPrincipal(student);
        String token = jwtTokenProvider.generateToken(principal);

        PaymentCreateRequest request = new PaymentCreateRequest(
                studentPlan.getPlanId(),
                new BigDecimal("1.00"),
                PaymentMethod.SEPAY,
                "Khoa hoc Machine Learning",
                "http://localhost:3000/result"
        );

        var result = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("SEPAY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentUrl").exists())
                .andReturn();

        com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(
                result.getResponse().getContentAsString());
        String paymentUrl = jsonNode.get("paymentUrl").asText();
        String transactionId = jsonNode.get("transactionId").asText();

        assertThat(paymentUrl).startsWith("https://vietqr.app/img?")
                .contains("amount=150000", "des=" + transactionId, "acc=0123456789");

        String rawBody = objectMapper.writeValueAsString(java.util.Map.of(
                "id", 92704,
                "gateway", "Vietcombank",
                "accountNumber", "0123456789",
                "code", transactionId,
                "content", transactionId + " thanh toan",
                "transferType", "in",
                "transferAmount", 150000,
                "referenceCode", "FT24012345678"
        ));
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String signature = "sha256=" + hmacSha256("test-sepay-webhook-secret", timestamp + "." + rawBody);

        mockMvc.perform(post("/api/payments/webhook/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SePay-Timestamp", timestamp)
                        .header("X-SePay-Signature", signature)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Kiểm tra DB: Payment thành công, invoice được tạo, learning access được cấp
        Payment paid = paymentRepository.findByTransactionId(transactionId).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paid.getPaymentMethod()).isEqualTo(PaymentMethod.SEPAY);

        var invoices = invoiceRepository.findByUserUserId(student.getUserId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getAmount()).isEqualByComparingTo("150000.00");

        var accesses = learningAccessRepository.findByUserUserId(student.getUserId());
        assertThat(accesses).hasSize(1);
        assertThat(accesses.get(0).getProductId()).isEqualTo(studentPlan.getPlanId());
        assertThat(subscriptionRepository.findByUserUserIdOrderByCreatedAtDesc(student.getUserId())).hasSize(1);

        // SePay có thể retry cùng webhook; không được tạo quyền/hóa đơn lần hai.
        mockMvc.perform(post("/api/payments/webhook/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SePay-Timestamp", timestamp)
                        .header("X-SePay-Signature", signature)
                        .content(rawBody))
                .andExpect(status().isOk());
        assertThat(invoiceRepository.findByUserUserId(student.getUserId())).hasSize(1);
        assertThat(learningAccessRepository.findByUserUserId(student.getUserId())).hasSize(1);
    }

    private String hmacSha256(String secret, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
