package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.ClassProgressUpdateRequest;
import com.example.demo.dto.response.ClassProgressResponse;
import com.example.demo.service.ClassLectureProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Class Progress", description = "API theo dõi tiến độ bài giảng và điều kiện hoàn tiền")
@RestController
@RequestMapping("/api/classes/{classId}")
@RequiredArgsConstructor
public class ClassProgressController {

    private final ClassLectureProgressService progressService;

    @Operation(summary = "Ghi nhận tiến độ học bài giảng", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/lectures/{lectureId}/progress")
    public ResponseEntity<Map<String, Boolean>> recordProgress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer classId,
            @PathVariable Long lectureId,
            @Valid @RequestBody ClassProgressUpdateRequest request) {
        progressService.recordProgress(principal.getUserId(), classId, lectureId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Xem tổng quan tiến độ học và trạng thái điều kiện hoàn tiền", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/progress")
    public ResponseEntity<ClassProgressResponse> getProgress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer classId) {
        return ResponseEntity.ok(progressService.getProgress(principal.getUserId(), classId));
    }
}
