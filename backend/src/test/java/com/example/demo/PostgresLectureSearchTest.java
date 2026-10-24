package com.example.demo;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Run against a disposable PostgreSQL database: Hibernate creates/drops its tables. */
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_POSTGRES_URL}",
        "spring.datasource.username=${TEST_POSTGRES_USER}",
        "spring.datasource.password=${TEST_POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.flyway.enabled=false", "spring.jpa.show-sql=false",
        "spring.config.import=", "app.video.enabled=false", "gemini.api.key=",
        "google.oauth.client-id=", "brevo.api-key="
})
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class PostgresLectureSearchTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired LectureRepository lectures;
    @Autowired LectureCollaboratorRepository collaborators;
    User teacher;
    Lecture owned;

    @BeforeEach void setup() {
        teacher = user(UserRole.TEACHER);
        owned = lecture(teacher, "PostgreSQL Alpha");
        lecture(teacher, "PostgreSQL Beta");
        Lecture shared = lecture(user(UserRole.TEACHER), "PostgreSQL Shared");
        lecture(user(UserRole.TEACHER), "Unrelated");
        LectureCollaborator collaborator = new LectureCollaborator();
        collaborator.setLecture(shared); collaborator.setCollaboratorUserId(teacher.getUserId());
        collaborator.setCreatedBy(shared.getTeacher().getUserId()); collaborators.save(collaborator);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "TEACHER"})
    void absentBlankAndMixedCaseTitleWorkWithContentAndCountQueries(UserRole role) throws Exception {
        User actor = role == UserRole.ADMIN ? user(role) : teacher;
        String token = jwt.generateToken(new UserPrincipal(actor));
        var request = get("/api/lectures").header("Authorization", "Bearer " + token)
                .param("size", "1"); // force execution of the count query
        mvc.perform(get("/api/lectures").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(role == UserRole.ADMIN ? 4 : 3));
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(role == UserRole.ADMIN ? 4 : 3));
        if (role == UserRole.ADMIN) request.param("teacherId", teacher.getUserId().toString());
        int expected = role == UserRole.ADMIN ? 2 : 3;
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(expected));
        mvc.perform(request.param("title", "   ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected));
        mvc.perform(get("/api/lectures").header("Authorization", "Bearer " + token)
                        .param("title", "pOsTgReSqL aLpHa").param("status", "DRAFT")
                        .param("videoStatus", "NOT_REQUESTED").param("accessScope", "PRIVATE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lectureId").value(owned.getLectureId()));
        mvc.perform(get("/api/lectures").header("Authorization", "Bearer " + token)
                        .param("title", "no matches " + UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    User user(UserRole role) {
        User user = new User(); user.setName("Search test"); user.setRole(role);
        user.setStatus(UserStatus.ACTIVE); user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("unused"); return users.save(user);
    }
    Lecture lecture(User owner, String title) {
        Lecture lecture = new Lecture(); lecture.setTeacher(owner); lecture.setTitle(title);
        return lectures.save(lecture);
    }
}
