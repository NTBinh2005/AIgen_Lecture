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
        "spring.datasource.url=jdbc:h2:mem:integration-flow-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "google.oauth.client-id=fake-google-client-id"
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

    @Autowired
    private com.example.demo.gateway.VnpayGateway vnpayGateway;

    private User student;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        learningAccessRepository.deleteAll();
        invoiceRepository.deleteAll();
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void testPaymentWebhook_FullFlowAndIdempotency() {
        // 1. Create Payment
        PaymentResponse paymentResp = paymentService.createPayment(
                student.getUserId(),
                new PaymentCreateRequest(999, new BigDecimal("150000.00"))
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
        assertThat(accesses.get(0).getProductId()).isEqualTo(999);
        assertThat(accesses.get(0).getAccessType()).isEqualTo("PREMIUM");

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
        // Before grant
        mockMvc.perform(get("/api/access/check-user")
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
                        .param("userId", student.getUserId().toString())
                        .param("productId", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getUserId()))
                .andExpect(jsonPath("$.productId").value(500))
                .andExpect(jsonPath("$.hasAccess").value(true));
    }

    @Test
    void testVnpayPaymentCreationAndIpnWebhook() throws Exception {
        UserPrincipal principal = new UserPrincipal(student);
        String token = jwtTokenProvider.generateToken(principal);

        // 1. Tạo payment VNPay qua REST API
        PaymentCreateRequest request = new PaymentCreateRequest(
                888,
                new BigDecimal("200000.00"),
                PaymentMethod.VNPAY,
                "Khoa hoc Machine Learning",
                "http://localhost:3000/result"
        );

        var result = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentUrl").exists())
                .andReturn();

        com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(
                result.getResponse().getContentAsString());
        String paymentUrl = jsonNode.get("paymentUrl").asText();
        String transactionId = jsonNode.get("transactionId").asText();

        assertThat(paymentUrl).contains("vnp_SecureHash=");
        assertThat(paymentUrl).contains("vnp_Amount=20000000");

        // 2. Parse query params từ paymentUrl để mô phỏng VNPay IPN webhook
        String query = paymentUrl.substring(paymentUrl.indexOf('?') + 1);
        java.util.Map<String, String> ipnParams = new java.util.HashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = java.net.URLDecoder.decode(pair.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8);
                String v = java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8);
                ipnParams.put(k, v);
            }
        }
        ipnParams.put("vnp_ResponseCode", "00");
        String validHash = vnpayGateway.computeSignature(ipnParams);
        ipnParams.put("vnp_SecureHash", validHash);

        var mockReq = get("/api/payments/webhook/vnpay");
        ipnParams.forEach(mockReq::param);

        // Gọi VNPay webhook IPN
        mockMvc.perform(mockReq)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        // 3. Kiểm tra DB: Payment thành công, invoice được tạo, learning access được cấp
        Payment paid = paymentRepository.findByTransactionId(transactionId).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paid.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);

        var invoices = invoiceRepository.findByUserUserId(student.getUserId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getAmount()).isEqualByComparingTo("200000.00");

        var accesses = learningAccessRepository.findByUserUserId(student.getUserId());
        assertThat(accesses).hasSize(1);
        assertThat(accesses.get(0).getProductId()).isEqualTo(888);
    }
}
