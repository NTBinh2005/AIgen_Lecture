package com.example.demo.service;

import com.example.demo.dto.request.CommentCreateRequest;
import com.example.demo.dto.response.CommentResponse;

import java.util.List;

public interface CommentService {
    List<CommentResponse> getComments(Long lectureId);

    CommentResponse addComment(Long lectureId, Integer userId, CommentCreateRequest request);

    void deleteComment(Long commentId, Integer requestUserId, boolean isAdmin);
}
