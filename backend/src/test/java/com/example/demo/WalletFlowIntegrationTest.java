package com.example.demo;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.ClassOrderRequest;
import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalCreateRequest;
import com.example.demo.dto.request.WithdrawalReviewRequest;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.ClassOrderService;
import com.example.demo.service.PremiumAccessService;
import com.example.demo.service.WalletService;
import com.example.demo.service.WithdrawalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_WALLET_DB_URL:jdbc:h2:mem:wallet-flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${TEST_WALLET_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${TEST_WALLET_DB_USER:sa}", "spring.datasource.password=${TEST_WALLET_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.config.import=", "app.video.enabled=false", "gemini.api.key=", "google.oauth.client-id=",
        "spring.mail.username=", "sepay.bank-account=0123456789", "sepay.bank-name=Vietcombank",
        "sepay.webhook-account-number=0123456789", "sepay.refund-source-account=0987654321",
        "sepay.webhook-secret=test-sepay-secret", "sepay.qr-base-url=https://vietqr.app/img"
})
@AutoConfigureMockMvc
class WalletFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired ClassRepository classes;
    @Autowired ClassStudentRepository classStudents;
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository transactions;
    @Autowired ClassOrderRepository classOrders;
    @Autowired WithdrawalRequestRepository withdrawals;
    @Autowired PaymentRepository payments;
    @Autowired WalletService walletService;
    @Autowired ClassOrderService classOrderService;
    @Autowired WithdrawalService withdrawalService;
    @Autowired PremiumAccessService premiumAccessService;

    User teacher, student, admin;
    ClassEntity classEntity;

    @BeforeEach
    void setup() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        teacher = createUser("Teacher " + suffix, "teacher_" + suffix + "@test.com", UserRole.TEACHER);
        student = createUser("Student " + suffix, "student_" + suffix + "@test.com", UserRole.STUDENT);
        admin = createUser("Admin " + suffix, "admin_" + suffix + "@test.com", UserRole.ADMIN);

        classEntity = new ClassEntity();
        classEntity.setClassName("Khóa học Lập trình Web Fullstack");
        classEntity.setClassCode("WEB" + System.nanoTime() % 1000000);
        classEntity.setTeacher(teacher);
        classEntity.setStatus(ClassStatus.ACTIVE);
        classEntity.setPrice(new BigDecimal("200000.00"));
        classEntity.setStartsAt(LocalDateTime.now().minusDays(1));
        classEntity = classes.save(classEntity);
    }

    private User createUser(String name, String email, UserRole role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$abcdefghij");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    private String token(User user) {
        return jwt.generateToken(new UserPrincipal(user));
    }

    @Test
    void testStudentIsExemptFromPlatformSubscription() {
        // Học sinh không bắt buộc phải có gói thành viên nền tảng nữa
        premiumAccessService.requireActivePlan(student.getUserId());
        premiumAccessService.requireStudentPlan(student.getUserId());
    }

    @Test
    void testDepositAndSepayWebhookFlow() throws Exception {
        // 1. Học sinh tạo yêu cầu nạp tiền 100,000 VNĐ
        DepositRequest req = new DepositRequest();
        req.setAmount(new BigDecimal("100000"));

        var result = mvc.perform(post("/api/wallets/deposit")
                        .header("Authorization", "Bearer " + token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").exists())
                .andExpect(jsonPath("$.amount").value(100000))
                .andReturn();

        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        String orderCode = json.path("orderCode").asText();
        assertThat(orderCode).startsWith("ND");

        // 2. Giả lập SePay gửi webhook báo đã nhận 100,000 VNĐ với memo là orderCode
        long eventId = 99001L;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = mapper.writeValueAsString(Map.of(
                "id", eventId,
                "transferType", "in",
                "transferAmount", 100000,
                "accountNumber", "0123456789",
                "code", orderCode,
                "referenceCode", "BANK-TXN-12345"
        ));
        String signature = "sha256=" + hmacSha256("test-sepay-secret", timestamp + "." + body);

        mvc.perform(post("/api/payments/webhook/sepay")
                        .header("X-SePay-Timestamp", timestamp)
                        .header("X-SePay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 3. Kiểm tra số dư ví học sinh được cộng 100,000 VNĐ
        Wallet studentWallet = wallets.findByUserUserId(student.getUserId()).orElseThrow();
        assertThat(studentWallet.getBalance()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    void sepayClassOrderStaysPendingUntilWebhookThenEnrollsAndCreditsTeacherOnce() throws Exception {
        ClassOrderRequest req = new ClassOrderRequest();
        req.setClassId(classEntity.getClassId());
        req.setPaymentMethod("SEPAY_QR");

        var result = mvc.perform(post("/api/class-orders/purchase")
                        .header("Authorization", "Bearer " + token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String orderCode = mapper.readTree(result.getResponse().getContentAsString()).path("orderCode").asText();
        String originalQrUrl = mapper.readTree(result.getResponse().getContentAsString()).path("qrUrl").asText();
        Payment payment = payments.findByTransactionId(orderCode).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(classStudents.findById(new ClassStudentId(classEntity.getClassId(), student.getUserId()))).isEmpty();
        assertThat(wallets.findByUserUserId(teacher.getUserId())).isEmpty();

        // Clicking create QR again should reopen this pending order, not fail or create another charge.
        var reopened = mvc.perform(post("/api/class-orders/purchase")
                        .header("Authorization", "Bearer " + token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderCode").value(orderCode))
                .andReturn();
        assertThat(mapper.readTree(reopened.getResponse().getContentAsString()).path("qrUrl").asText())
                .isEqualTo(originalQrUrl);

        postSepayPayment(orderCode, 99002L);

        ClassOrder paidOrder = classOrders.findByOrderCode(orderCode).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(ClassOrderStatus.ACTIVE);
        assertThat(payments.findByTransactionId(orderCode).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(classStudents.findById(new ClassStudentId(classEntity.getClassId(), student.getUserId()))
                .orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        Wallet teacherWallet = wallets.findByUserUserId(teacher.getUserId()).orElseThrow();
        assertThat(teacherWallet.getPendingBalance()).isEqualByComparingTo(new BigDecimal("190000.00"));

        postSepayPayment(orderCode, 99003L);
        teacherWallet = wallets.findByUserUserId(teacher.getUserId()).orElseThrow();
        assertThat(teacherWallet.getPendingBalance()).isEqualByComparingTo(new BigDecimal("190000.00"));
        assertThat(transactions.findByWalletWalletIdOrderByCreatedAtDesc(teacherWallet.getWalletId(),
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent())
                .filteredOn(tx -> tx.getType() == WalletTransactionType.CLASS_INCOME_PENDING)
                .hasSize(1);
    }

    private void postSepayPayment(String orderCode, long eventId) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = mapper.writeValueAsString(Map.of(
                "id", eventId,
                "transferType", "in",
                "transferAmount", 200000,
                "accountNumber", "0123456789",
                // SePay can leave `code` null and provide the transfer memo only in `content`.
                "content", orderCode + "-221026-17:59:34 BANK-REF",
                "referenceCode", "BANK-TXN-" + eventId
        ));
        String signature = "sha256=" + hmacSha256("test-sepay-secret", timestamp + "." + body);

        mvc.perform(post("/api/payments/webhook/sepay")
                        .header("X-SePay-Timestamp", timestamp)
                        .header("X-SePay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void testPurchaseClassWithWalletAndRefund() throws Exception {
        // Cấp 500,000 VNĐ vào ví học sinh
        Wallet studentWallet = walletService.getOrCreateWallet(student.getUserId());
        walletService.credit(studentWallet, new BigDecimal("500000.00"), WalletTransactionType.DEPOSIT, "Test deposit", "TEST-INIT");

        // Học sinh mua lớp học (giá 200,000 VNĐ) bằng ví
        ClassOrderRequest orderReq = new ClassOrderRequest();
        orderReq.setClassId(classEntity.getClassId());
        orderReq.setPaymentMethod("WALLET");

        mvc.perform(post("/api/class-orders/purchase")
                        .header("Authorization", "Bearer " + token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(orderReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(200000.0))
                .andExpect(jsonPath("$.platformFeeAmount").value(10000.0)) // 5% của 200,000 là 10,000
                .andExpect(jsonPath("$.teacherAmount").value(190000.0)) // 95% của 200,000 là 190,000
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Kiểm tra số dư ví học sinh còn 300,000 VNĐ
        studentWallet = wallets.findByUserUserId(student.getUserId()).orElseThrow();
        assertThat(studentWallet.getBalance()).isEqualByComparingTo(new BigDecimal("300000.00"));

        // Kiểm tra ví giáo viên có pendingBalance = 190,000 VNĐ
        Wallet teacherWallet = wallets.findByUserUserId(teacher.getUserId()).orElseThrow();
        assertThat(teacherWallet.getPendingBalance()).isEqualByComparingTo(new BigDecimal("190000.00"));
        assertThat(teacherWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // Kiểm tra học sinh đã được ghi danh ACTIVE vào lớp
        ClassStudent cs = classStudents.findById(new ClassStudentId(classEntity.getClassId(), student.getUserId())).orElseThrow();
        assertThat(cs.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);

        // Học sinh yêu cầu hoàn học phí (chưa xem quá 5% bài giảng và trong vòng 4 ngày)
        mvc.perform(post("/api/class-orders/classes/{classId}/refund", classEntity.getClassId())
                        .header("Authorization", "Bearer " + token(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hoàn tiền học phí về ví thành công"));

        // Sau khi hoàn tiền:
        // Học sinh nhận lại 200,000 VNĐ vào ví (300k + 200k = 500k)
        studentWallet = wallets.findByUserUserId(student.getUserId()).orElseThrow();
        assertThat(studentWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500000.00"));

        // Giáo viên bị trừ 190,000 VNĐ khỏi pendingBalance
        teacherWallet = wallets.findByUserUserId(teacher.getUserId()).orElseThrow();
        assertThat(teacherWallet.getPendingBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // Ghi danh lớp của học sinh bị hủy (CANCELLED)
        cs = classStudents.findById(new ClassStudentId(classEntity.getClassId(), student.getUserId())).orElseThrow();
        assertThat(cs.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void testTeacherWithdrawalFlow() throws Exception {
        // Cấp 500,000 VNĐ vào balance khả dụng của giáo viên
        Wallet teacherWallet = walletService.getOrCreateWallet(teacher.getUserId());
        walletService.credit(teacherWallet, new BigDecimal("500000.00"), WalletTransactionType.CLASS_INCOME_AVAILABLE, "Available income", "TEST");

        // Giáo viên tạo yêu cầu rút 200,000 VNĐ về ngân hàng
        WithdrawalCreateRequest req = new WithdrawalCreateRequest();
        req.setAmount(new BigDecimal("200000.00"));
        req.setBankName("Vietcombank");
        req.setBankAccount("0123456789");
        req.setAccountHolder("TEACHER ONE");

        var result = mvc.perform(post("/api/withdrawals")
                        .header("Authorization", "Bearer " + token(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200000.0))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();

        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        int withdrawalId = json.path("withdrawalId").asInt();

        // Kiểm tra tiền đã bị khóa: balance còn 300,000 VNĐ, lockedBalance = 200,000 VNĐ
        teacherWallet = wallets.findByUserUserId(teacher.getUserId()).orElseThrow();
        assertThat(teacherWallet.getBalance()).isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(teacherWallet.getLockedBalance()).isEqualByComparingTo(new BigDecimal("200000.00"));

        // Admin duyệt và hoàn tất rút tiền
        WithdrawalReviewRequest reviewReq = new WithdrawalReviewRequest();
        reviewReq.setStatus("COMPLETED");
        reviewReq.setAdminNote("Đã chuyển khoản thành công");

        mvc.perform(patch("/api/withdrawals/admin/{id}", withdrawalId)
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Kiểm tra tiền lockedBalance đã bị trừ hoàn tất: balance = 300,000 VNĐ, lockedBalance = 0
        teacherWallet = wallets.findByUserUserId(teacher.getUserId()).orElseThrow();
        assertThat(teacherWallet.getBalance()).isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(teacherWallet.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
