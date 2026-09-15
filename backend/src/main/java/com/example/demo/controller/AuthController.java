package com.example.demo.controller;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.GoogleLoginRequest;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.SmsLoginRequest;
import com.example.demo.dto.request.SmsOtpRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.OtpResponse;
import com.example.demo.entity.AuthProvider;
import com.example.demo.entity.User;
import com.example.demo.entity.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Register, login, Google login, SMS OTP login")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;

    @Operation(summary = "Dang ky tai khoan moi")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email da duoc su dung: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);

        User saved = userRepository.save(user);
        UserPrincipal principal = new UserPrincipal(saved);
        String token = jwtTokenProvider.generateToken(principal);

        return ResponseEntity.ok(new AuthResponse(
                token,
                saved.getUserId(),
                saved.getName(),
                saved.getEmail(),
                saved.getRole()
        ));
    }

    @Operation(summary = "Dang nhap va nhan JWT token")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Email hoac password khong dung"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoac password khong dung");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Tai khoan da bi vo hieu hoa");
        }

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtTokenProvider.generateToken(principal);

        return ResponseEntity.ok(new AuthResponse(
                token,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        ));
    }

    @Operation(summary = "Dang nhap bang Google ID token")
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    @Operation(summary = "Gui ma OTP dang nhap qua SMS")
    @PostMapping("/sms/request-otp")
    public ResponseEntity<OtpResponse> requestSmsOtp(@Valid @RequestBody SmsOtpRequest request) {
        return ResponseEntity.ok(authService.requestSmsOtp(request));
    }

    @Operation(summary = "Xac thuc OTP SMS va nhan JWT token")
    @PostMapping("/sms/verify")
    public ResponseEntity<AuthResponse> verifySmsOtp(@Valid @RequestBody SmsLoginRequest request) {
        return ResponseEntity.ok(authService.verifySmsOtp(request));
    }
}
