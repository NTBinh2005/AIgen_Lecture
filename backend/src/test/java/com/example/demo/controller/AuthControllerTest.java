package com.example.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.service.EmailService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.subscriptions.enforce-premium=false",
        "spring.datasource.url=jdbc:h2:mem:auth-controller-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "google.oauth.client-id=210319750681-8gddmp142h4ihi44ohus3b26j70u63r4.apps.googleusercontent.com"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void googleLoginRejectsBlankIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleLoginRejectsInvalidIdTokenWhenClientIdConfigured() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"fake-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRegistrationCannotCreateAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Attacker","email":"attacker@example.com",
                                 "password":"Password123!","role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationDoesNotCreateAccountUntilOtpVerifiedAndWrongOtpReturns400WithMessage() throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        doAnswer(invocation -> {
            sentBody.set(invocation.getArgument(2));
            return null;
        }).when(emailService).sendEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pending","email":"pending@example.com",
                                 "password":"Password123!","role":"STUDENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mã OTP đã được gửi tới email của bạn"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isUnauthorized());

        String otp = sentBody.get().replaceAll("(?s).*?(\\d{6}).*", "$1");
        String wrongOtp = otp.equals("000000") ? "999999" : "000000";

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"otp\":\"" + wrongOtp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng"));

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP đã hết hạn hoặc không tồn tại"));

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pending@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void registrationEmailFailureReturns503AndDoesNotBlockImmediateRetry() throws Exception {
        doThrow(new ApiException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        String payload = """
                {"name":"Retry User","email":"retry-smtp@example.com",
                 "password":"Password123!","role":"TEACHER"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Không thể gửi email xác nhận, vui lòng thử lại sau"));

        AtomicReference<String> sentBody = new AtomicReference<>();
        doAnswer(invocation -> {
            sentBody.set(invocation.getArgument(2));
            return null;
        }).when(emailService).sendEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        assertThat(sentBody.get()).isNotBlank();
    }
}
