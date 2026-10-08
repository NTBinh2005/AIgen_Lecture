package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.UserCreateRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.dto.request.UserUpdateRequest;
import com.example.demo.entity.AuthProvider;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.entity.AuditAction;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuditService;
import java.util.List;

import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, AuditService auditService, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> searchStudentsByEmail(String email) {
        String keyword = requireText(email, "email");
        return userRepository
                .findTop20ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(UserRole.STUDENT, keyword)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Integer userId) {
        return toResponse(getUser(userId));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String email = requireText(request.email(), "email");
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists");
        }

        User user = new User();
        user.setRole(request.role());
        user.setName(requireText(request.name(), "name"));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(requireText(request.passwordHash(), "passwordHash")));
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);

        User saved = userRepository.save(user);
        auditService.log(saved.getUserId(), AuditAction.USER_REGISTER, "USER", String.valueOf(saved.getUserId()),
                "User created by admin with email: " + saved.getEmail());

        return toResponse(saved);
    }

    @Transactional
    public UserResponse update(Integer userId, UserUpdateRequest request, Integer currentUserId) {
        User user = getUser(userId);

        if (request.role() != null) {
            if (user.getRole() == UserRole.ADMIN && request.role() != UserRole.ADMIN) {
                if (userId.equals(currentUserId)) {
                    throw new BadRequestException("Admin không thể tự hạ quyền của chính mình");
                }
                long adminCount = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
                if (adminCount <= 1) {
                    throw new BadRequestException("Hệ thống phải có ít nhất 1 ADMIN hoạt động");
                }
            }
            if (user.getRole() != request.role()) {
                auditService.log(user.getUserId(), AuditAction.ROLE_CHANGED, "USER", String.valueOf(user.getUserId()),
                        "Role changed from " + user.getRole() + " to " + request.role());
            }
            user.setRole(request.role());
        }
        if (request.name() != null) {
            user.setName(requireText(request.name(), "name"));
        }
        if (request.email() != null) {
            String email = requireText(request.email(), "email");
            if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndUserIdNot(email, userId)) {
                throw new BadRequestException("Email already exists");
            }
            user.setEmail(email);
        }
        if (request.passwordHash() != null) {
            user.setPasswordHash(passwordEncoder.encode(requireText(request.passwordHash(), "passwordHash")));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        return toResponse(user);
    }

    @Transactional
    public void deactivate(Integer userId, Integer currentUserId) {
        User user = getUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            if (userId.equals(currentUserId)) {
                throw new BadRequestException("Admin không thể tự vô hiệu hóa tài khoản của chính mình");
            }
            long adminCount = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
            if (adminCount <= 1) {
                throw new BadRequestException("Hệ thống phải có ít nhất 1 ADMIN hoạt động");
            }
        }
        user.setStatus(UserStatus.INACTIVE);
    }

    private User getUser(Integer userId) {
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

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
