package com.example.demo.controller;

import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.request.LectureUpdateRequest;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.dto.response.VideoStatusResponse;
import com.example.demo.entity.Lecture;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.service.DocumentParserService;
import com.example.demo.service.LectureService;
import com.example.demo.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import com.example.demo.dto.response.LectureGenerateResponse;

/**
 * REST API Controller cho Lecture.
 *
 * Phân quyền qua JWT:
 *   - POST   /api/lectures       → TEACHER only
 *   - GET    /api/lectures       → TEACHER (danh sách của mình)
 *   - GET    /api/lectures/{id}  → TEACHER sở hữu hoặc STUDENT
 *   - DELETE /api/lectures/{id}  → TEACHER sở hữu
 */
@Tag(name = "Lectures", description = "API quản lý bài giảng và trạng thái video")
@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final DocumentParserService documentParserService;
    private final LlmService llmService;

    /**
     * Tích hợp LLM: Upload file (PDF, DOCX, PPTX) -> AI sinh kịch bản bài giảng
     */
    @Operation(summary = "Sinh kịch bản bài giảng từ File", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/generate-from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<LectureGenerateResponse> generateFromFile(
            @RequestParam("file") MultipartFile file) {
        String text = documentParserService.parseDocument(file);
        LectureGenerateResponse response = llmService.generateLectureScript(text);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/lectures
     * Teacher tạo mới một bài giảng và trigger video generation.
     * TeacherId lấy từ JWT token, không cần truyền qua request param.
     */
    @Operation(summary = "Tạo mới bài giảng và trigger video generation (async)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<LectureResponse> createLecture(
            @Valid @RequestBody LectureCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LectureResponse response = lectureService.createLecture(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /api/lectures
     * Lấy danh sách bài giảng của teacher đang đăng nhập (có filter, sort, paging).
     */
    @Operation(summary = "Lấy danh sách bài giảng của teacher (có filter, sort, paging)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Page<LectureResponse>> getLectures(
            @RequestParam(required = false) String title,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {

        Page<LectureResponse> page = lectureService.getLecturesByTeacher(
                principal.getUserId(), title, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/lectures/student
     * Lấy danh sách tất cả bài giảng cho Student (có filter, sort, paging).
     */
    @Operation(summary = "Lấy danh sách bài giảng cho student (toàn hệ thống)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Page<LectureResponse>> getStudentLectures(
            @RequestParam(required = false) String title,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<LectureResponse> page = lectureService.getAllLecturesForStudent(title, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/lectures/{id}
     * Lấy chi tiết một bài giảng.
     * Teacher: chỉ xem được của mình (BR-03).
     * Student: xem được tất cả (không có ownership check).
     */
    @Operation(summary = "Lấy chi tiết bài giảng theo ID",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public ResponseEntity<LectureResponse> getLecture(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        LectureResponse response = lectureService.getLecture(id, principal.getUserId(), principal);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/lectures/{id}/video-status
     * Poll trạng thái render video. Endpoint này PUBLIC (không cần token)
     * vì student cần poll liên tục ngay sau khi nhận lectureId từ link chia sẻ.
     */
    @Operation(summary = "Poll trạng thái render video của bài giảng")
    @GetMapping("/{id}/video-status")
    public ResponseEntity<VideoStatusResponse> getVideoStatus(@PathVariable Long id) {
        Lecture lecture = lectureService.getVideoStatus(id);
        VideoStatusResponse response = VideoStatusResponse.from(
                lecture.getLectureId(),
                lecture.getVideoStatus(),
                lecture.getVideoUrl(),
                null
        );
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/lectures/{id}
     * Cập nhật tiêu đề bài giảng. Chỉ teacher sở hữu mới được cập nhật.
     */
    @Operation(summary = "Cập nhật tiêu đề bài giảng",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<LectureResponse> updateLecture(
            @PathVariable Long id,
            @Valid @RequestBody LectureUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LectureResponse response = lectureService.updateLectureTitle(id, principal.getUserId(), request.getTitle());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/lectures/{id}
     * Soft-delete bài giảng (BR-08). Chỉ teacher sở hữu mới được xóa.
     */
    @Operation(summary = "Xóa mềm bài giảng (soft-delete theo BR-08)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteLecture(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        lectureService.deleteLecture(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
