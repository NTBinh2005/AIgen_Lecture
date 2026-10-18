package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
    void registrationDoesNotCreateAccountUntilOtpVerified() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pending","email":"pending@example.com",
                                 "password":"Password123!","role":"STUDENT"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"otp\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }
}
