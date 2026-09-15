package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.GoogleLoginRequest;
import com.example.demo.dto.request.SmsLoginRequest;
import com.example.demo.dto.request.SmsOtpRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.OtpResponse;
import com.example.demo.entity.AuthProvider;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import com.example.demo.service.SmsService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final int OTP_LENGTH = 6;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final String PHONE_EMAIL_DOMAIN = "@phone.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SmsService smsService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RestClient restClient = RestClient.create();
    private final Map<String, OtpChallenge> otpChallenges = new ConcurrentHashMap<>();

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Value("${auth.sms.otp-expiration-seconds:300}")
    private int otpExpirationSeconds;

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenInfo tokenInfo = verifyGoogleToken(request.getIdToken());
        String email = requireText(tokenInfo.email(), "Google account email").toLowerCase(Locale.ROOT);
        String googleSubject = requireText(tokenInfo.subject(), "Google subject");

        User user = userRepository.findByGoogleSubject(googleSubject)
                .or(() -> userRepository.findByEmail(email))
                .map(existing -> linkGoogleAccount(existing, googleSubject))
                .orElseGet(() -> createGoogleUser(tokenInfo, request.getRole()));

        ensureActive(user);
        return buildAuthResponse(user);
    }

    @Override
    public OtpResponse requestSmsOtp(SmsOtpRequest request) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        String otp = String.format("%0" + OTP_LENGTH + "d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(otpExpirationSeconds);

        otpChallenges.put(phoneNumber, new OtpChallenge(otp, expiresAt, 0));
        smsService.sendOtp(phoneNumber, otp);

        return new OtpResponse("OTP da duoc gui qua SMS", otpExpirationSeconds);
    }

    @Override
    @Transactional
    public AuthResponse verifySmsOtp(SmsLoginRequest request) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        verifyOtp(phoneNumber, request.getOtp());

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> createPhoneUser(phoneNumber, request));

        ensureActive(user);
        return buildAuthResponse(user);
    }

    private GoogleTokenInfo verifyGoogleToken(String idToken) {
        if (!StringUtils.hasText(googleClientId)) {
            throw new BadRequestException("GOOGLE_OAUTH_CLIENT_ID chua duoc cau hinh");
        }

        String uri = UriComponentsBuilder
                .fromUriString("https://oauth2.googleapis.com/tokeninfo")
                .queryParam("id_token", idToken)
                .build()
                .toUriString();

        GoogleTokenInfo tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(GoogleTokenInfo.class);
        } catch (Exception ex) {
            throw new BadCredentialsException("Google ID token khong hop le");
        }

        if (tokenInfo == null || !Boolean.TRUE.equals(tokenInfo.emailVerified())) {
            throw new BadCredentialsException("Google email chua duoc xac thuc");
        }
        if (!googleClientId.equals(tokenInfo.audience())) {
            throw new BadCredentialsException("Google ID token khong dung client id");
        }
        return tokenInfo;
    }

    private User linkGoogleAccount(User user, String googleSubject) {
        if (!StringUtils.hasText(user.getGoogleSubject())) {
            user.setGoogleSubject(googleSubject);
        }
        return user;
    }

    private User createGoogleUser(GoogleTokenInfo tokenInfo, UserRole requestedRole) {
        User user = new User();
        user.setName(resolveName(tokenInfo.name(), tokenInfo.email()));
        user.setEmail(tokenInfo.email().toLowerCase(Locale.ROOT));
        user.setGoogleSubject(tokenInfo.subject());
        user.setPasswordHash(generateUnusedPasswordHash());
        user.setRole(resolveRole(requestedRole));
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.GOOGLE);
        return userRepository.save(user);
    }

    private User createPhoneUser(String phoneNumber, SmsLoginRequest request) {
        User user = new User();
        user.setName(StringUtils.hasText(request.getName()) ? request.getName().trim() : phoneNumber);
        user.setEmail(toInternalPhoneEmail(phoneNumber));
        user.setPhoneNumber(phoneNumber);
        user.setPasswordHash(generateUnusedPasswordHash());
        user.setRole(resolveRole(request.getRole()));
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.PHONE);
        return userRepository.save(user);
    }

    private void verifyOtp(String phoneNumber, String otp) {
        OtpChallenge challenge = otpChallenges.get(phoneNumber);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            otpChallenges.remove(phoneNumber);
            throw new BadCredentialsException("Ma OTP da het han hoac khong ton tai");
        }
        if (challenge.attempts() >= MAX_OTP_ATTEMPTS) {
            otpChallenges.remove(phoneNumber);
            throw new BadCredentialsException("Ma OTP da bi khoa do nhap sai qua nhieu lan");
        }
        if (!challenge.otp().equals(otp)) {
            otpChallenges.put(phoneNumber, challenge.nextAttempt());
            throw new BadCredentialsException("Ma OTP khong dung");
        }
        otpChallenges.remove(phoneNumber);
    }

    private String normalizePhoneNumber(String rawPhoneNumber) {
        String phoneNumber = requireText(rawPhoneNumber, "phoneNumber")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
        if (!phoneNumber.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new BadRequestException("So dien thoai phai dung dinh dang E.164, vi du +84901234567");
        }
        return phoneNumber;
    }

    private AuthResponse buildAuthResponse(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtTokenProvider.generateToken(principal);
        boolean phoneOnlyAccount = user.getAuthProvider() == AuthProvider.PHONE;
        return new AuthResponse(
                token,
                user.getUserId(),
                user.getName(),
                phoneOnlyAccount ? null : user.getEmail(),
                user.getRole(),
                user.getPhoneNumber(),
                user.getAuthProvider() != null ? user.getAuthProvider().name() : AuthProvider.LOCAL.name()
        );
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Tai khoan da bi vo hieu hoa");
        }
    }

    private String resolveName(String googleName, String email) {
        if (StringUtils.hasText(googleName)) {
            return googleName.trim();
        }
        return email.substring(0, email.indexOf('@'));
    }

    private UserRole resolveRole(UserRole role) {
        return role != null ? role : UserRole.STUDENT;
    }

    private String generateUnusedPasswordHash() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private String toInternalPhoneEmail(String phoneNumber) {
        String normalized = phoneNumber.substring(1);
        return "phone-" + normalized + PHONE_EMAIL_DOMAIN;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record OtpChallenge(String otp, Instant expiresAt, int attempts) {
        OtpChallenge nextAttempt() {
            return new OtpChallenge(otp, expiresAt, attempts + 1);
        }
    }

    private record GoogleTokenInfo(
            @JsonProperty("sub") String subject,
            @JsonProperty("email") String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            @JsonProperty("name") String name,
            @JsonProperty("aud") String audience
    ) {
    }
}
