package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO để tạo một comment mới.
 * Dùng cho POST /api/lectures/{id}/comments
 */
public record CommentCreateRequest(
    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(max = 2000, message = "Bình luận tối đa 2000 ký tự")
    String content,

    /** null nếu là comment gốc, có giá trị nếu là reply */
    Long parentCommentId
) {}
