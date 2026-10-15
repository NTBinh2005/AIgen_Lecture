package com.example.demo.service;

import com.example.demo.dto.request.CommentCreateRequest;
import com.example.demo.dto.response.CommentResponse;

import java.util.List;

public interface CommentService {
    org.springframework.data.domain.Page<CommentResponse> getComments(
            Long lectureId, org.springframework.data.domain.Pageable pageable);

    CommentResponse addComment(Long lectureId, Integer userId, CommentCreateRequest request);

    void deleteComment(Long commentId, Integer requestUserId, boolean isAdmin);
}
