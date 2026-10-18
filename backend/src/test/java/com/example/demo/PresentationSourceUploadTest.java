package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.LlmService;
import com.example.demo.service.SmsService;
import com.example.demo.service.serviceImpl.SlideAiClient;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tạo bản trình chiếu từ file: loại file được nhận khớp với giao diện, lỗi có lý do tiếng Việt. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:presentation-upload;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.config.import=", "app.video.enabled=false", "gemini.api.key=", "google.oauth.client-id=",
        "app.subscriptions.enforce-premium=false", "spring.mail.username="
})
@AutoConfigureMockMvc
class PresentationSourceUploadTest {
    private static final String PPTX_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    // Không gọi AI thật: job chạy nền sau khi nhận file, kết quả không quan trọng với test này
    @MockitoBean SlideAiClient slideAi;
    @MockitoBean LlmService llm;
    @MockitoBean SmsService sms;

    User teacher;

    @BeforeEach
    void setup() {
        teacher = new User();
        teacher.setRole(UserRole.TEACHER);
        teacher.setStatus(UserStatus.ACTIVE);
        teacher.setName("Upload teacher");
        teacher.setEmail(UUID.randomUUID() + "@example.com");
        teacher.setPasswordHash(passwords.encode("Password123!"));
        teacher = users.save(teacher);
    }

    @Test
    void pdfWithOpenActionIsAccepted() throws Exception {
        // Word/LaTeX thường thêm /OpenAction để đặt trang mở đầu
        upload("bai-giang.pdf", "application/pdf",
                pdf("1 0 obj<</Type/Catalog/Pages 2 0 R/OpenAction[3 0 R/Fit]>>endobj"))
                .andExpect(status().isAccepted());
    }

    @Test
    void pdfWithJavaScriptIsRejectedWithVietnameseReason() throws Exception {
        upload("doc-hai.pdf", "application/pdf",
                pdf("1 0 obj<</Type/Catalog/OpenAction<</S/JavaScript/JS(app.alert(1))>>>>endobj"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("mã tự chạy")));
    }

    @Test
    void pptxIsAcceptedLikeTheFormSays() throws Exception {
        upload("slide-cu.pptx", PPTX_TYPE, pptx()).andExpect(status().isAccepted());
    }

    @Test
    void unsupportedExtensionExplainsAllowedTypes() throws Exception {
        upload("ghi-chu.txt", "text/plain", "xin chào".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ hỗ trợ file PDF, DOCX hoặc PPTX"));
    }

    private ResultActions upload(String filename, String contentType, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/presentations/from-file")
                .file(new MockMultipartFile("file", filename, contentType, bytes))
                .param("title", "Thử tải file")
                .param("templateId", "standard")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(teacher))));
    }

    private static byte[] pdf(String catalog) {
        return ("%PDF-1.4\n" + catalog + "\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] pptx() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            show.createSlide();
            show.write(out);
            return out.toByteArray();
        }
    }
}
