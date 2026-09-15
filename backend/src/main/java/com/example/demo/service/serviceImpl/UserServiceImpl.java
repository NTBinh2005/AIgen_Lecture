package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.UserCreateRequest;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.dto.request.UserUpdateRequest;
import com.example.demo.entity.AuthProvider;
import com.example.demo.entity.User;
import com.example.demo.entity.UserStatus;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.repository.UserRepository;
import java.util.List;

import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
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
        user.setPasswordHash(requireText(request.passwordHash(), "passwordHash"));
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Integer userId, UserUpdateRequest request) {
        User user = getUser(userId);

        if (request.role() != null) {
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
            user.setPasswordHash(requireText(request.passwordHash(), "passwordHash"));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        return toResponse(user);
    }

    @Transactional
    public void deactivate(Integer userId) {
        User user = getUser(userId);
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
