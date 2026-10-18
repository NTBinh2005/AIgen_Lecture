package com.example.demo.controller;

import com.example.demo.common.response.ApiError;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.ClassPresentationRequest;
import com.example.demo.dto.response.ClassPresentationResponse;
import com.example.demo.service.ClassPresentationService;
import com.example.demo.service.serviceImpl.SlideAiClient;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giao bản trình chiếu cho lớp và cho học sinh xem (giống luồng bài giảng).
 * Quyền chi tiết (quản lý lớp, ghi danh ACTIVE, gói đăng ký) kiểm tra ở ClassPresentationService.
 */
@RestController
@RequiredArgsConstructor
public class ClassPresentationController {
    private final ClassPresentationService classPresentationService;

    @PostMapping("/api/classes/{classId}/presentations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Giao bản trình chiếu đã xuất bản cho lớp")
    public ClassPresentationResponse assign(
            @PathVariable Integer classId,
            @Valid @RequestBody ClassPresentationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return classPresentationService.assign(classId, request.presentationId(), requireUserId(principal));
    }

    @GetMapping("/api/classes/{classId}/presentations")
    @Operation(summary = "Danh sách bản trình chiếu đã giao cho lớp")
    public List<ClassPresentationResponse> findByClass(
            @PathVariable Integer classId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return classPresentationService.findByClass(classId, requireUserId(principal));
    }

    @DeleteMapping("/api/classes/{classId}/presentations/{presentationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bỏ giao bản trình chiếu khỏi lớp")
    public void unassign(
            @PathVariable Integer classId,
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        classPresentationService.unassign(classId, presentationId, requireUserId(principal));
    }

    @GetMapping("/api/student/presentations/{presentationId}")
    @Operation(summary = "Học sinh xem thông tin bản trình chiếu được giao")
    public ClassPresentationResponse studentView(
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return classPresentationService.getForStudent(presentationId, requireUserId(principal));
    }

    /** HTML phiên bản đã xuất bản; frontend nhúng bằng iframe srcdoc có sandbox. */
    @GetMapping("/api/student/presentations/{presentationId}/render")
    @Operation(summary = "Học sinh xem trình chiếu (HTML) của bản được giao")
    public ResponseEntity<String> studentRender(
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        String html = classPresentationService.renderForStudent(presentationId, requireUserId(principal));
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox allow-scripts")
                .cacheControl(CacheControl.noStore())
                .body(html);
    }

    @ExceptionHandler(SlideAiClient.SlideAiException.class)
    ResponseEntity<ApiError> slideAiUnavailable(
            SlideAiClient.SlideAiException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "SLIDE_AI_UNAVAILABLE",
                "Dịch vụ trình chiếu slide đang tạm ngưng, vui lòng thử lại sau",
                request.getRequestURI()));
    }

    private static Integer requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Yêu cầu đăng nhập");
        }
        return principal.getUserId();
    }
}
