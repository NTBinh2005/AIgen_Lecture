package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.LectureCollaboratorRequest;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.request.LecturePublishRequest;
import com.example.demo.dto.request.LectureUpdateRequest;
import com.example.demo.dto.response.LectureAsyncResponse;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.dto.response.LectureVersionResponse;
import com.example.demo.dto.response.VideoStatusResponse;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.LectureAccessScope;
import com.example.demo.service.LectureGenerationWorkflowService;
import com.example.demo.service.LectureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {
    private final LectureService lectureService;
    private final LectureGenerationWorkflowService generationWorkflowService;

    /** LECT-02/AC-01: validate upload, persist Asset and return an async job immediately. */
    @PostMapping(
            value = {"/from-file", "/generate-from-file"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LectureAsyncResponse> generateFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(defaultValue = "PRIVATE") LectureAccessScope accessScope,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        LectureAsyncResponse response = generationWorkflowService.start(
                actor.getUserId(), title, accessScope, file, idempotencyKey);
        return ResponseEntity.accepted().body(response);
    }

    /** LECT-01: a manual lecture starts as DRAFT. */
    @PostMapping
    public ResponseEntity<LectureResponse> createLecture(
            @Valid @RequestBody LectureCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        LectureResponse response = lectureService.createLecture(actor.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<LectureResponse>> getLectures(
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(lectureService.getLecturesByTeacher(
                actor.getUserId(), title, pageable));
    }

    /** Legacy route is deliberately fail-closed until Backend 2 supplies access grants. */
    @GetMapping("/student")
    public ResponseEntity<Page<LectureResponse>> getStudentLectures(
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        requirePrincipal(principal);
        return ResponseEntity.ok(lectureService.getAllLecturesForStudent(title, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LectureResponse> getLecture(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        return ResponseEntity.ok(lectureService.getLecture(id, actor.getUserId(), actor));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<LectureVersionResponse>> versions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        return ResponseEntity.ok(lectureService.getVersions(
                id, actor.getUserId(), isAdmin(actor)));
    }

    @GetMapping("/{id}/versions/{versionId}")
    public ResponseEntity<LectureVersionResponse> version(
            @PathVariable Long id,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        return ResponseEntity.ok(lectureService.getVersion(
                id, versionId, actor.getUserId(), actor));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LectureResponse> patchLecture(
            @PathVariable Long id,
            @Valid @RequestBody LectureUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(lectureService.updateLecture(
                id, actor.getUserId(), isAdmin(actor), request));
    }

    /** Compatibility alias for the previous title-only contract. */
    @PutMapping("/{id}")
    public ResponseEntity<LectureResponse> updateLecture(
            @PathVariable Long id,
            @Valid @RequestBody LectureUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return patchLecture(id, request, principal);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<LectureResponse> publish(
            @PathVariable Long id,
            @RequestBody(required = false) LecturePublishRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        UUID versionId = request == null ? null : request.versionId();
        return ResponseEntity.ok(lectureService.publishLecture(
                id, versionId, actor.getUserId(), isAdmin(actor)));
    }

    @GetMapping("/{id}/collaborators")
    public ResponseEntity<List<Integer>> collaborators(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(lectureService.getCollaborators(
                id, actor.getUserId(), isAdmin(actor)));
    }

    @PostMapping("/{id}/collaborators")
    public ResponseEntity<Void> addCollaborator(
            @PathVariable Long id,
            @Valid @RequestBody LectureCollaboratorRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        lectureService.addCollaborator(
                id, request.userId(), actor.getUserId(), isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/collaborators/{userId}")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable Long id,
            @PathVariable Integer userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        lectureService.removeCollaborator(
                id, userId, actor.getUserId(), isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        lectureService.archiveLecture(id, actor.getUserId(), isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/video-status")
    public ResponseEntity<VideoStatusResponse> getVideoStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        requirePrincipal(principal);
        Lecture lecture = lectureService.getVideoStatus(id);
        return ResponseEntity.ok(VideoStatusResponse.from(
                lecture.getLectureId(),
                lecture.getVideoStatus(),
                lecture.getVideoUrl(),
                null));
    }

    private UserPrincipal requirePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return principal;
    }

    private UserPrincipal requireTeacherOrAdmin(UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        boolean allowed = actor.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_TEACHER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
        if (!allowed) {
            throw new AccessDeniedException("Only teachers and administrators may manage lectures");
        }
        return actor;
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
