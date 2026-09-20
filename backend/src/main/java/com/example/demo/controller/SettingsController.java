package com.example.demo.controller;

import com.example.demo.dto.request.ChangePasswordRequest;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller cho tính năng Cài đặt cá nhân.
 *
 * Endpoints:
 *   GET  /api/settings/profile  → Lấy thông tin hồ sơ user hiện tại
 *   PUT  /api/settings/profile  → Cập nhật tên & email
 *   PUT  /api/settings/password → Đổi mật khẩu
 *
 * Tất cả endpoints đều yêu cầu xác thực JWT (bất kỳ role nào).
 */
@Tag(name = "Settings", description = "API cài đặt cá nhân — cập nhật hồ sơ & đổi mật khẩu")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @Operation(
        summary = "Lấy thông tin hồ sơ của user đang đăng nhập",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(settingsService.getProfile(principal.getUserId()));
    }

    @Operation(
        summary = "Cập nhật tên & email của user đang đăng nhập",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(settingsService.updateProfile(principal.getUserId(), request));
    }

    @Operation(
        summary = "Đổi mật khẩu — xác minh mật khẩu cũ trước khi cập nhật",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        settingsService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
