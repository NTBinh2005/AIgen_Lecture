package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.ChangePasswordRequest;
import com.example.demo.dto.request.UpdateProfileRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.entity.AuditAction;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuditService;
import com.example.demo.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý các thao tác liên quan đến cài đặt cá nhân của user.
 * - Cập nhật thông tin hồ sơ (tên, email)
 * - Đổi mật khẩu (verify mật khẩu cũ trước khi cập nhật)
 */
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Lấy thông tin hồ sơ của user hiện tại.
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Integer userId) {
        User user = getUserOrThrow(userId);
        return toResponse(user);
    }

    /**
     * Cập nhật tên và email của user.
     * Kiểm tra email mới không trùng với user khác.
     */
    @Transactional
    public UserResponse updateProfile(Integer userId, UpdateProfileRequest request) {
        User user = getUserOrThrow(userId);

        String newEmail = request.email().trim();
        if (!newEmail.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmailAndUserIdNot(newEmail, userId)) {
            throw new BadRequestException("Email này đã được sử dụng bởi tài khoản khác");
        }

        user.setName(request.name().trim());
        user.setEmail(newEmail);

        return toResponse(userRepository.save(user));
    }

    /**
     * Đổi mật khẩu: xác minh mật khẩu cũ, mã hoá và lưu mật khẩu mới.
     */
    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        User user = getUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không chính xác");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditService.log(user.getUserId(), AuditAction.PASSWORD_CHANGE, "USER", String.valueOf(user.getUserId()), "User changed password");
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private User getUserOrThrow(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getRole(),
                user.getName(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
