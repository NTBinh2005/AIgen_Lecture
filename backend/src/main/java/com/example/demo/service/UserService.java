package com.example.demo.service;

import com.example.demo.dto.request.UserCreateRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.dto.request.UserUpdateRequest;

import java.util.List;

public interface UserService {
    List<UserResponse> findAll();

    /** FIX #23: Tìm học sinh theo email để giáo viên ghi danh vào lớp. */
    List<UserResponse> searchStudentsByEmail(String email);

    UserResponse findById(Integer userId);

    UserResponse create(UserCreateRequest request);

    UserResponse update(Integer userId, UserUpdateRequest request, Integer currentUserId);

    void deactivate(Integer userId, Integer currentUserId);
}
