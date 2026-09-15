package com.example.demo.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho một Comment, bao gồm cả danh sách replies (1 cấp).
 * Dùng cho GET /api/lectures/{id}/comments
 */
public record CommentResponse(
    Long commentId,
    Long parentCommentId,
    Integer userId,
    String userName,
    String userRole,       // "TEACHER" | "STUDENT" | "ADMIN"
    String content,
    LocalDateTime createdAt,
    List<CommentResponse> replies  // chỉ có giá trị với comment gốc
) {}
