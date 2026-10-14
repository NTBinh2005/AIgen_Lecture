package com.example.demo.controller;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.PresentationCollaboratorRequest;
import com.example.demo.dto.request.PresentationCreateFromLectureRequest;
import com.example.demo.dto.request.PresentationPublishRequest;
import com.example.demo.dto.request.PresentationUpdateRequest;
import com.example.demo.dto.response.PresentationAsyncResponse;
import com.example.demo.dto.response.PresentationExportResponse;
import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.dto.response.PresentationVersionResponse;
import com.example.demo.service.PresentationExportContent;
import com.example.demo.service.PresentationGenerationWorkflowService;
import com.example.demo.service.PresentationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/presentations")
@RequiredArgsConstructor
public class PresentationController {
    private final PresentationService presentationService;
    private final PresentationGenerationWorkflowService generationWorkflowService;
    private final ObjectMapper objectMapper;

    @PostMapping("/from-lecture")
    public ResponseEntity<PresentationAsyncResponse> fromLecture(
            @Valid @RequestBody PresentationCreateFromLectureRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.accepted().body(generationWorkflowService.fromLecture(
                actor.getUserId(), isAdmin(actor), request, idempotencyKey));
    }

    @PostMapping(value = "/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PresentationAsyncResponse> fromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String templateId,
            @RequestParam(required = false) String parameters,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.accepted().body(generationWorkflowService.fromFile(
                actor.getUserId(),
                title,
                templateId,
                parseParameters(parameters),
                file,
                idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<Page<PresentationResponse>> list(
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.list(actor.getUserId(), title, pageable));
    }

    @GetMapping("/{presentationId}")
    public ResponseEntity<PresentationResponse> get(
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.get(
                presentationId, actor.getUserId(), isAdmin(actor)));
    }

    @GetMapping("/{presentationId}/versions")
    public ResponseEntity<List<PresentationVersionResponse>> versions(
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.versions(
                presentationId, actor.getUserId(), isAdmin(actor)));
    }

    @PatchMapping("/{presentationId}")
    public ResponseEntity<PresentationResponse> update(
            @PathVariable UUID presentationId,
            @Valid @RequestBody PresentationUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.update(
                presentationId, request, actor.getUserId(), isAdmin(actor)));
    }

    @PostMapping("/{presentationId}/versions/{versionId}/restore")
    public ResponseEntity<PresentationResponse> restore(
            @PathVariable UUID presentationId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.restore(
                presentationId, versionId, actor.getUserId(), isAdmin(actor)));
    }

    @PostMapping("/{presentationId}/publish")
    public ResponseEntity<PresentationResponse> publish(
            @PathVariable UUID presentationId,
            @RequestBody(required = false) PresentationPublishRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        UUID versionId = request == null ? null : request.versionId();
        return ResponseEntity.ok(presentationService.publish(
                presentationId, versionId, actor.getUserId(), isAdmin(actor)));
    }

    @GetMapping("/{presentationId}/collaborators")
    public ResponseEntity<List<Integer>> collaborators(
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.collaborators(
                presentationId, actor.getUserId(), isAdmin(actor)));
    }

    @PostMapping("/{presentationId}/collaborators")
    public ResponseEntity<Void> addCollaborator(
            @PathVariable UUID presentationId,
            @Valid @RequestBody PresentationCollaboratorRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        presentationService.addCollaborator(
                presentationId, request.userId(), actor.getUserId(), isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{presentationId}/collaborators/{userId}")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable UUID presentationId,
            @PathVariable Integer userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        presentationService.removeCollaborator(
                presentationId, userId, actor.getUserId(), isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{presentationId}/versions/{versionId}/exports")
    public ResponseEntity<PresentationExportResponse> export(
            @PathVariable UUID presentationId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        return ResponseEntity.ok(presentationService.export(
                presentationId, versionId, actor.getUserId(), isAdmin(actor)));
    }

    @GetMapping("/{presentationId}/exports/{exportId}/content")
    public ResponseEntity<byte[]> downloadExport(
            @PathVariable UUID presentationId,
            @PathVariable UUID exportId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        PresentationExportContent content = presentationService.downloadExport(
                presentationId, exportId, actor.getUserId(), isAdmin(actor));
        byte[] bytes = content.getBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }

    @DeleteMapping("/{presentationId}")
    public ResponseEntity<Void> archive(
            @PathVariable UUID presentationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requireTeacherOrAdmin(principal);
        presentationService.archive(presentationId, actor.getUserId(), isAdmin(actor));
        return ResponseEntity.noContent().build();
    }

    private JsonNode parseParameters(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(parameters);
            if (!parsed.isObject()) {
                throw new BadRequestException("parameters must be a JSON object");
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("parameters must be valid JSON");
        }
    }

    private UserPrincipal requireTeacherOrAdmin(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        boolean allowed = principal.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_TEACHER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
        if (!allowed) {
            throw new AccessDeniedException("Only teachers and administrators may manage presentations");
        }
        return principal;
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
