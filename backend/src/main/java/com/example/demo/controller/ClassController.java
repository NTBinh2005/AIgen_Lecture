package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.ClassCreateRequest;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.request.ClassUpdateRequest;
import com.example.demo.service.ClassService;
import com.example.demo.service.serviceImpl.ClassServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classes")
@Tag(name = "Classes")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassServiceImpl classService) {
        this.classService = classService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all classes")
    public List<ClassResponse> findAll() {
        return classService.findAll();
    }

    @GetMapping("/{classId}")
    @Operation(summary = "Get class by id")
    public ClassResponse findById(@PathVariable Integer classId) {
        return classService.findById(classId);
    }

    /**
     * ENRL-01: Teacher xem danh sách lớp mình phụ trách.
     * Route: GET /api/classes/my
     */
    @GetMapping("/my")
    @Operation(summary = "ENRL-01: Get my classes (TEACHER)")
    public List<ClassResponse> findMyClasses(@AuthenticationPrincipal UserPrincipal principal) {
        return classService.findByTeacher(principal.getUserId());
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * CLASS-AC-01: Tạo lớp mới (status = DRAFT).
     * Yêu cầu role TEACHER hoặc ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "CLASS-AC-01: Create class (TEACHER / ADMIN)")
    public ClassResponse create(
            @Valid @RequestBody ClassCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return classService.create(request, principal.getUserId());
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * CLASS-BR-02: Chỉ teacher chính / co-teacher / admin được update.
     * CLASS-BR-05: Đổi teacher chính ghi audit log.
     */
    @PatchMapping("/{classId}")
    @Operation(summary = "CLASS-BR-02/05: Update class")
    public ClassResponse update(
            @PathVariable Integer classId,
            @Valid @RequestBody ClassUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return classService.update(classId, request, principal.getUserId());
    }

    /**
     * CLASS-AC-01: Kích hoạt lớp DRAFT → ACTIVE.
     * Yêu cầu className, classCode, startsAt đã có.
     */
    @PatchMapping("/{classId}/activate")
    @Operation(summary = "CLASS-AC-01: Activate class DRAFT → ACTIVE")
    public ClassResponse activate(
            @PathVariable Integer classId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return classService.activate(classId, principal.getUserId());
    }

    /**
     * CLASS-BR-04 + Exception: Đóng lớp ACTIVE → CLOSED.
     * Không thể đóng khi có live session đang live.
     */
    @PatchMapping("/{classId}/close")
    @Operation(summary = "CLASS-BR-04: Close class ACTIVE → CLOSED")
    public ClassResponse close(
            @PathVariable Integer classId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return classService.close(classId, principal.getUserId());
    }

    // ── DELETE (legacy deactivate) ────────────────────────────────────────────

    @DeleteMapping("/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate class (admin only, use /close instead)")
    public void deactivate(@PathVariable Integer classId) {
        classService.deactivate(classId);
    }
}
