package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.request.PresentationUpdateRequest;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassStatus;
import com.example.demo.entity.ClassStudent;
import com.example.demo.entity.ClassStudentId;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.PresentationSourceType;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PresentationService;
import com.example.demo.service.SmsService;
import com.example.demo.service.serviceImpl.SlideAiClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Luồng bản trình chiếu giống bài giảng: xuất bản -> giao cho lớp -> học sinh ghi danh ACTIVE xem được. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:class-presentation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.config.import=", "app.video.enabled=false", "gemini.api.key=", "google.oauth.client-id=",
        "app.subscriptions.enforce-premium=false", "spring.mail.username="
})
@AutoConfigureMockMvc
class ClassPresentationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired ClassRepository classes;
    @Autowired ClassStudentRepository enrollments;
    @Autowired NotificationRepository notifications;
    @Autowired PresentationService presentations;
    @Autowired PasswordEncoder passwords;
    @MockitoBean SlideAiClient slideAi;
    @MockitoBean SmsService sms;

    User teacher, otherTeacher, student, outsider;
    ClassEntity classroom;
    UUID presentationId;
    /** Tiêu đề riêng mỗi test: cache HTML khoá theo nội dung, tránh dùng chung giữa các test */
    String title;

    @BeforeEach
    void setup() {
        teacher = user(UserRole.TEACHER);
        otherTeacher = user(UserRole.TEACHER);
        student = user(UserRole.STUDENT);
        outsider = user(UserRole.STUDENT);
        classroom = new ClassEntity();
        classroom.setTeacher(teacher);
        classroom.setClassName("Trình chiếu " + UUID.randomUUID());
        classroom.setClassCode(UUID.randomUUID().toString().substring(0, 12));
        classroom.setStatus(ClassStatus.ACTIVE);
        classroom.setMaxStudents(20);
        classroom = classes.save(classroom);
        ClassStudent enrollment = new ClassStudent();
        enrollment.setClassEntity(classroom);
        enrollment.setStudent(student);
        enrollment.setId(new ClassStudentId(classroom.getClassId(), student.getUserId()));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollments.save(enrollment);

        title = "Nhập môn học máy " + UUID.randomUUID().toString().substring(0, 8);
        presentationId = presentations.createGenerating(teacher.getUserId(), title,
                PresentationSourceType.ASSET, null, UUID.randomUUID(), "academic", JsonNodeFactory.instance.objectNode())
                .getPresentationId();
        presentations.completeGeneration(presentationId, teacher.getUserId(), title, List.of(
                slide("Mở đầu", 0), slide("Nội dung", 1), slide("Kết luận", 2)));
        when(slideAi.render(any(), anyString())).thenReturn("<html><body>deck</body></html>");
    }

    @Test
    void publishedPresentationAssignedToClassIsVisibleToEnrolledStudentsOnly() throws Exception {
        // Chưa xuất bản thì chưa giao được
        mvc.perform(assign(teacher)).andExpect(status().isBadRequest());

        presentations.publish(presentationId, null, teacher.getUserId(), false);
        mvc.perform(assign(otherTeacher)).andExpect(status().isForbidden());
        mvc.perform(assign(teacher)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.slideCount").value(3));

        assertThat(notifications.findAll()).anyMatch(n -> n.getUserId().equals(student.getUserId())
                && n.getType() == NotificationType.PRESENTATION_ASSIGNED);

        String classPath = "/api/classes/" + classroom.getClassId() + "/presentations";
        mvc.perform(get(classPath).header("Authorization", bearer(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].presentationId").value(presentationId.toString()))
                .andExpect(jsonPath("$[0].title").value(title));
        mvc.perform(get(classPath).header("Authorization", bearer(outsider))).andExpect(status().isForbidden());

        String studentPath = "/api/student/presentations/" + presentationId;
        mvc.perform(get(studentPath).header("Authorization", bearer(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$.slideCount").value(3))
                // Học sinh không nhận nội dung slide thô (có ghi chú của giáo viên)
                .andExpect(jsonPath("$.slides").doesNotExist());
        mvc.perform(get(studentPath + "/render").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("<html><body>deck</body></html>"));
        mvc.perform(get(studentPath + "/render").header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());

        // Bỏ giao: học sinh mất quyền xem
        mvc.perform(delete(classPath + "/" + presentationId).header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get(studentPath + "/render").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivedPresentationDisappearsFromClassAndStudentAccess() throws Exception {
        presentations.publish(presentationId, null, teacher.getUserId(), false);
        mvc.perform(assign(teacher)).andExpect(status().isCreated());
        presentations.archive(presentationId, teacher.getUserId(), false);

        mvc.perform(get("/api/classes/" + classroom.getClassId() + "/presentations")
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/student/presentations/" + presentationId).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void slideAiOutageWithoutCachedRenderReturnsServiceUnavailable() throws Exception {
        // Service tắt từ đầu: dựng sẵn khi giao lớp cũng thất bại nên chưa có bản lưu
        when(slideAi.render(any(), anyString())).thenThrow(new SlideAiClient.SlideAiException("down"));
        presentations.publish(presentationId, null, teacher.getUserId(), false);
        mvc.perform(assign(teacher)).andExpect(status().isCreated());
        mvc.perform(get("/api/student/presentations/" + presentationId + "/render")
                        .header("Authorization", bearer(student)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SLIDE_AI_UNAVAILABLE"));
    }

    @Test
    void cachedRenderIsServedWhileSlideAiIsDown() throws Exception {
        presentations.publish(presentationId, null, teacher.getUserId(), false);
        mvc.perform(assign(teacher)).andExpect(status().isCreated());
        String renderPath = "/api/student/presentations/" + presentationId + "/render";
        mvc.perform(get(renderPath).header("Authorization", bearer(student))).andExpect(status().isOk());

        when(slideAi.render(any(), anyString())).thenThrow(new SlideAiClient.SlideAiException("down"));
        mvc.perform(get(renderPath).header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(content().string("<html><body>deck</body></html>"));
        // Giáo viên trình chiếu cùng nội dung cũng dùng được bản đã lưu
        mvc.perform(get("/api/presentations/" + presentationId + "/render").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(content().string("<html><body>deck</body></html>"));
    }

    @Test
    void cacheIsKeyedByContentSoEditedSlidesAreNotServedStale() throws Exception {
        presentations.publish(presentationId, null, teacher.getUserId(), false);
        mvc.perform(get("/api/presentations/" + presentationId + "/render").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk());
        // Giáo viên sửa slide (phiên bản mới, nội dung khác) rồi service tắt: không trả HTML cũ
        presentations.update(presentationId, new PresentationUpdateRequest(title, List.of(
                slide("Mở đầu mới", 0), slide("Nội dung", 1), slide("Kết luận", 2))), teacher.getUserId(), false);
        when(slideAi.render(any(), anyString())).thenThrow(new SlideAiClient.SlideAiException("down"));
        mvc.perform(get("/api/presentations/" + presentationId + "/render").header("Authorization", bearer(teacher)))
                .andExpect(status().isServiceUnavailable());
    }

    private org.springframework.test.web.servlet.RequestBuilder assign(User actor) {
        return post("/api/classes/" + classroom.getClassId() + "/presentations")
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentationId\":\"" + presentationId + "\"}");
    }

    private static PresentationSlideRequest slide(String title, int order) {
        return new PresentationSlideRequest(title, List.of("Ý 1", "Ý 2"), "Ghi chú riêng của giáo viên",
                "TITLE_AND_CONTENT", order);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateToken(new UserPrincipal(user));
    }

    private User user(UserRole role) {
        User user = new User();
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setName("Flow " + role);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwords.encode("Password123!"));
        return users.save(user);
    }
}
