package com.example.demo.service;

import com.example.demo.dto.request.ChangePasswordRequest;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.dto.response.UserResponse;

public interface SettingsService {
    UserResponse getProfile(Integer userId);

    UserResponse updateProfile(
            Integer userId,
            UpdateProfileRequest request
    );

    void changePassword(
            Integer userId,
            ChangePasswordRequest request
    );
}
