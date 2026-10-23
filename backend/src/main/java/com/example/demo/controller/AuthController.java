package com.example.demo.controller;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.GoogleLoginRequest;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.LogoutRequest;
import com.example.demo.dto.request.RefreshTokenRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.VerifyRegisterRequest;
import com.example.demo.dto.request.SmsLoginRequest;
import com.example.demo.dto.request.SmsOtpRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.OtpResponse;
import com.example.demo.entity.AuthProvider;
import com.example.demo.entity.User;
import com.example.demo.entity.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import com.example.demo.service.RefreshTokenService;
import com.example.demo.entity.AuditAction;
import com.example.demo.service.AuditService;
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
import java.util.Locale;

@Tag(name = "Authentication", description = "Register, login, Google login, SMS OTP login, refresh token, logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final com.example.demo.service.LoginThrottleService loginThrottleService;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @Operation(summary = "Dang ky buoc 1 — luu dang ky tam va gui OTP qua email (chua tao tai khoan)")
    @PostMapping("/register")
    public ResponseEntity<OtpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.requestRegistration(request));
    }

    @Operation(summary = "Dang ky buoc 2 — xac nhan OTP email, tao tai khoan va tra ve accessToken + refreshToken")
    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> verifyRegister(@Valid @RequestBody VerifyRegisterRequest request) {
        return ResponseEntity.ok(authService.verifyRegistration(request));
    }

    @Operation(summary = "Dang nhap bang email/password — tra ve accessToken + refreshToken")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String throttleKey = loginThrottleService.key(email, servletRequest.getRemoteAddr());
        loginThrottleService.assertAllowed(throttleKey);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginThrottleService.failed(throttleKey);
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Tài khoản đã bị vô hiệu hoá");
        }
        loginThrottleService.succeeded(throttleKey);

        auditService.log(user.getUserId(), AuditAction.USER_LOGIN, "USER", String.valueOf(user.getUserId()), "User logged in with email: " + user.getEmail());

        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtTokenProvider.generateToken(principal);
        String rawRefreshToken = refreshTokenService.createRefreshToken(user);

        return ResponseEntity.ok(new AuthResponse(
                accessToken,
                rawRefreshToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                null,
                AuthProvider.LOCAL.name()
        ));
    }


    @Operation(summary = "Lam moi accessToken bang refreshToken (token rotation)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Dang xuat — thu hoi refreshToken hien tai")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
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

    @Operation(summary = "Xac thuc OTP SMS va nhan accessToken + refreshToken")
    @PostMapping("/sms/verify")
    public ResponseEntity<AuthResponse> verifySmsOtp(@Valid @RequestBody SmsLoginRequest request) {
        return ResponseEntity.ok(authService.verifySmsOtp(request));
    }

    @Operation(summary = "Xac thuc JWT token cho cac service khac — tra ve identity, role, permissions")
    @PostMapping("/verify")
    public ResponseEntity<com.example.demo.dto.response.TokenVerifyResponse> verifyToken(
            @Valid @RequestBody com.example.demo.dto.request.TokenVerifyRequest request) {
        com.example.demo.dto.response.TokenVerifyResponse result = authService.verifyToken(request.token());
        return ResponseEntity.status(result.valid()
                ? org.springframework.http.HttpStatus.OK
                : org.springframework.http.HttpStatus.UNAUTHORIZED).body(result);
    }
}
