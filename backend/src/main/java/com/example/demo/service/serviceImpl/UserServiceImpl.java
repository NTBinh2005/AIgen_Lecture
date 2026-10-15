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
import java.util.Locale;

import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.example.demo.service.RefreshTokenService refreshTokens;
    private final org.springframework.context.ApplicationEventPublisher events;

    public UserServiceImpl(UserRepository userRepository, AuditService auditService, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            com.example.demo.service.RefreshTokenService refreshTokens, org.springframework.context.ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserResponse> findPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("page phải >= 0, size từ 1 đến 100");
        return userRepository.findAll(org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> searchTeachersByEmail(String email) {
        String keyword = requireSearchKeyword(email);
        return userRepository.findTop20ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(UserRole.TEACHER, keyword)
                .stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).map(this::toResponse).toList();
    }

    private String requireSearchKeyword(String email) {
        String keyword = requireText(email, "email");
        if (keyword.length() < 3 || keyword.length() > 100) {
            throw new BadRequestException("Từ khoá email phải có từ 3 đến 100 ký tự");
        }
        return keyword;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> searchStudentsByEmail(String email) {
        String keyword = requireSearchKeyword(email);
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
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Email đã được sử dụng");
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
        userRepository.lockActiveAdmins();
        User user = userRepository.lockByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        boolean revokeSessions = request.passwordHash() != null
                || (request.role() != null && request.role() != user.getRole())
                || (request.status() != null && request.status() != user.getStatus());

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
            String email = normalizeEmail(request.email());
            if ((user.isEmailAmbiguous() || !email.equalsIgnoreCase(user.getEmail()))
                    && userRepository.existsByEmailIgnoreCaseAndUserIdNot(email, userId)) {
                throw new BadRequestException("Email đã được sử dụng");
            }
            user.setEmail(email);
            user.setEmailAmbiguous(false);
        }
        if (request.passwordHash() != null) {
            user.setPasswordHash(passwordEncoder.encode(requireText(request.passwordHash(), "passwordHash")));
        }
        if (request.status() != null) {
            if (request.status() != UserStatus.ACTIVE && user.getRole() == UserRole.ADMIN) {
                if (userId.equals(currentUserId)) {
                    throw new BadRequestException("Admin không thể tự vô hiệu hóa tài khoản của chính mình");
                }
                if (user.getStatus() == UserStatus.ACTIVE
                        && userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
                    throw new BadRequestException("Hệ thống phải có ít nhất 1 ADMIN hoạt động");
                }
            }
            user.setStatus(request.status());
        }

        if (revokeSessions) {
            user.setTokenVersion(user.getTokenVersion() + 1);
            refreshTokens.revokeAllTokensForUser(userId);
            events.publishEvent(new com.example.demo.event.UserSessionsRevokedEvent(userId));
        }
        return toResponse(user);
    }

    @Transactional
    public void deactivate(Integer userId, Integer currentUserId) {
        userRepository.lockActiveAdmins();
        User user = userRepository.lockByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
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
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokens.revokeAllTokensForUser(userId);
        events.publishEvent(new com.example.demo.event.UserSessionsRevokedEvent(userId));
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
                user.getCreatedAt(),
                user.isEmailAmbiguous()
        );
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private String normalizeEmail(String value) {
        return requireText(value, "email").toLowerCase(Locale.ROOT);
    }
}
