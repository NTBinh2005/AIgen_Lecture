package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend2-api-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "google.oauth.client-id=test-client-id",
        "live.webhook.secret=test-webhook-secret",
        "internal.events.token=test-internal-token"
})
@AutoConfigureMockMvc
class Backend2ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void backend2EnforcesVisibilityOwnershipLifecycleAndRawBodyWebhookSignature() throws Exception {
        Account teacher1 = register("Teacher One", "teacher1.backend2@example.com", "TEACHER");
        Account teacher2 = register("Teacher Two", "teacher2.backend2@example.com", "TEACHER");
        Account student = register("Student One", "student.backend2@example.com", "STUDENT");

        String classBody = """
                {"teacherId":%d,"className":"Backend 2","classCode":"BE2-001",
                 "semester":"FA26","startsAt":"2026-10-01T08:00:00",
                 "endsAt":"2026-12-01T10:00:00","maxStudents":30}
                """.formatted(teacher1.userId());
        String classJson = mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearer(teacher1.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(classBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        int classId = objectMapper.readTree(classJson).get("classId").asInt();

        mockMvc.perform(get("/api/classes")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/classes/{classId}/students", classId)
                        .header("Authorization", bearer(teacher1.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + student.userId() + "}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/classes/{classId}/activate", classId)
                        .header("Authorization", bearer(teacher1.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/classes/{classId}/students", classId)
                        .header("Authorization", bearer(teacher1.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + student.userId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/classes")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classId").value(classId));

        String scheduleBody = """
                {"classId":%d,"startsAt":"2026-10-02T08:00:00",
                 "endsAt":"2026-10-02T10:00:00","type":"ONLINE","timezone":"Asia/Ho_Chi_Minh"}
                """.formatted(classId);
        mockMvc.perform(post("/api/schedules")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(scheduleBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/classes/{classId}", classId)
                        .header("Authorization", bearer(teacher2.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/classes/{classId}/students", classId)
                        .header("Authorization", bearer(teacher2.token())))
                .andExpect(status().isForbidden());

        String sessionBody = """
                {"classId":%d,"title":"Live API test","type":"ONLINE",
                 "startsAt":"2026-10-03T08:00:00","endsAt":"2026-10-03T10:00:00",
                 "meetingUrl":"https://video.test/meeting-be2","externalMeetingId":"meeting-be2"}
                """.formatted(classId);
        String sessionJson = mockMvc.perform(post("/api/live-sessions")
                        .header("Authorization", bearer(teacher1.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(sessionBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(sessionJson).get("sessionId").asLong();

        mockMvc.perform(get("/api/live-sessions/{sessionId}", sessionId)
                        .header("Authorization", bearer(teacher2.token())))
                .andExpect(status().isForbidden());

        String startedEvent = "{\"eventType\":\"meeting.started\",\"externalMeetingId\":\"meeting-be2\",\"timestamp\":1,\"payload\":{}}";
        mockMvc.perform(post("/api/webhooks/live/provider")
                        .header("X-Webhook-Signature", hmac(startedEvent))
                        .contentType(MediaType.APPLICATION_JSON).content(startedEvent))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/live-sessions/{sessionId}", sessionId)
                        .header("Authorization", bearer(teacher1.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));

        String endedEvent = "{\"eventType\":\"meeting.ended\",\"externalMeetingId\":\"meeting-be2\",\"timestamp\":2,\"payload\":{}}";
        mockMvc.perform(post("/api/webhooks/live/provider")
                        .header("X-Webhook-Signature", hmac(""))
                        .contentType(MediaType.APPLICATION_JSON).content(endedEvent))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/live-sessions/{sessionId}", sessionId)
                        .header("Authorization", bearer(teacher1.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));
    }

    private Account register(String name, String email, String role) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", name, "email", email, "password", "password123", "role", role));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new Account(json.get("userId").asInt(), json.get("token").asText());
    }

    private String hmac(String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(int userId, String token) {
    }
}
