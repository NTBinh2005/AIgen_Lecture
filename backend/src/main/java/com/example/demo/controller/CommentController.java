package com.example.demo.controller;

import com.example.demo.dto.request.CommentCreateRequest;
import com.example.demo.dto.response.CommentResponse;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller cho tính năng bình luận Q&A dưới video bài giảng.
 *
 * Endpoints:
 *   GET    /api/lectures/{lectureId}/comments             → Lấy toàn bộ comments (tất cả role)
 *   POST   /api/lectures/{lectureId}/comments             → Thêm comment mới (tất cả role)
 *   DELETE /api/lectures/{lectureId}/comments/{commentId} → Xóa comment (chủ sở hữu hoặc ADMIN)
 */
@Tag(name = "Comments", description = "API Q&A bình luận dưới video bài giảng")
@RestController
@RequestMapping("/api/lectures/{lectureId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(
        summary = "Lấy danh sách bình luận của bài giảng",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable Long lectureId) {

        return ResponseEntity.ok(commentService.getComments(lectureId));
    }

    @Operation(
        summary = "Thêm bình luận hoặc reply (tất cả role đã xác thực)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long lectureId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        CommentResponse response = commentService.addComment(
                lectureId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "Xóa bình luận (chủ sở hữu hoặc ADMIN)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long lectureId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        commentService.deleteComment(commentId, principal.getUserId(), isAdmin);
        return ResponseEntity.noContent().build();
    }
}
