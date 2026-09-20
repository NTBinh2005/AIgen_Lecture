package com.example.demo.service;

import com.example.demo.dto.request.UserCreateRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.dto.request.UserUpdateRequest;

import java.util.List;

public interface UserService {
    List<UserResponse> findAll();

    UserResponse findById(Integer userId);

    UserResponse create(UserCreateRequest request);

    UserResponse update(Integer userId, UserUpdateRequest request);

    void deactivate(Integer userId);
}
