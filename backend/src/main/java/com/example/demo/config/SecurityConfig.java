package com.example.demo.config;

import com.example.demo.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;

/**
 * Spring Security configuration.
 *
 * Các route công khai (không cần token):
 * - POST /api/auth/login
 * - POST /api/auth/register
 * - GET /api/lectures/{id}/video-status (student polling)
 * - Swagger UI
 *
 * Tất cả route còn lại yêu cầu JWT hợp lệ.
 * RBAC theo role (TEACHER / STUDENT / ADMIN) được bảo vệ ở từng endpoint.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints — public
                        .requestMatchers("/api/auth/**").permitAll()
                        // Swagger UI và Error — public
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/error")
                        .permitAll()
                        // Video status polling — public
                        .requestMatchers(HttpMethod.GET, "/api/lectures/*/video-status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/lectures/generate-from-file").permitAll()

                        // ── CLASS endpoints ───────────────────────────────────────────────
                        // Tạo lớp — TEACHER hoặc ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/classes").hasAnyRole("TEACHER", "ADMIN")
                        // Xem tất cả lớp
                        .requestMatchers(HttpMethod.GET, "/api/classes").authenticated()
                        // Xem lớp của mình — TEACHER
                        .requestMatchers(HttpMethod.GET, "/api/classes/my").hasRole("TEACHER")
                        // Kích hoạt / đóng lớp — TEACHER hoặc ADMIN
                        .requestMatchers(HttpMethod.PATCH, "/api/classes/*/activate").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/classes/*/close").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/classes/*/archive").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/classes/*/lectures").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/classes/*/lectures/*").hasAnyRole("TEACHER", "ADMIN")
                        // Update lớp — bất kỳ authenticated (service layer kiểm tra ownership)
                        .requestMatchers(HttpMethod.PATCH, "/api/classes/*").authenticated()
                        // Xóa lớp — ADMIN
                        .requestMatchers(HttpMethod.DELETE, "/api/classes/*").hasRole("ADMIN")
                        // Xem chi tiết lớp — authenticated
                        .requestMatchers(HttpMethod.GET, "/api/classes/*").authenticated()

                        // ── ENROLLMENT endpoints ──────────────────────────────────────────
                        // Admin xem tất cả enrollment
                        .requestMatchers(HttpMethod.GET, "/api/enrollments").hasRole("ADMIN")
                        // Danh sách student trong lớp — TEACHER hoặc ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/classes/*/students").hasAnyRole("TEACHER", "ADMIN")
                        // Student xem lớp của mình — STUDENT
                        .requestMatchers(HttpMethod.GET, "/api/students/me/classes").hasRole("STUDENT")
                        // Admin/Teacher xem lớp của student cụ thể
                        .requestMatchers(HttpMethod.GET, "/api/students/*/classes").hasAnyRole("TEACHER", "ADMIN")
                        // Ghi danh — TEACHER hoặc ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/classes/*/students").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/classes/*/students/bulk").hasAnyRole("TEACHER", "ADMIN")
                        // Self-enroll — STUDENT
                        .requestMatchers(HttpMethod.POST, "/api/classes/self-enroll").hasRole("STUDENT")
                        // Update/Hủy enrollment — TEACHER hoặc ADMIN
                        .requestMatchers(HttpMethod.PATCH, "/api/classes/*/students/*").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/classes/*/students/*").hasAnyRole("TEACHER", "ADMIN")

                        // Schedule writes are restricted here and ownership is enforced in the service.
                        .requestMatchers(HttpMethod.POST, "/api/schedules").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/schedules/*").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/schedules/*").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/schedules/**").authenticated()

                        // ── LECTURE endpoints ─────────────────────────────────────────────
                        // Teacher-only: tạo, xóa bài giảng
                        .requestMatchers(HttpMethod.POST, "/api/lectures").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/lectures/**").hasRole("TEACHER")

                        // ── LIVE SESSION endpoints ────────────────────────────────────────
                        // Tạo session — TEACHER hoặc ADMIN (LIVE-01, LIVE-BR-01)
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions").hasAnyRole("TEACHER", "ADMIN")
                        // Cập nhật / hủy / lifecycle — TEACHER hoặc ADMIN (LIVE-08)
                        .requestMatchers(HttpMethod.PATCH, "/api/live-sessions/*").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/cancel").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/open").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/live").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/end").hasAnyRole("TEACHER", "ADMIN")
                        // Join session — tất cả user đã authenticated (LIVE-02, LIVE-BR-01 check ở service)
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/join").authenticated()
                        // Xem session — authenticated
                        .requestMatchers(HttpMethod.GET, "/api/live-sessions/**").authenticated()
                        // Điểm danh manual — TEACHER (LIVE-06)
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/attendance/manual").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/live-sessions/*/participants").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/live-sessions/*/participants/*").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/resources").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/live-sessions/*/resources/*").hasAnyRole("TEACHER", "ADMIN")

                        // ── QR endpoints ──────────────────────────────────────────────────
                        // Tạo QR — TEACHER (LIVE-06)
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/qr/generate").hasAnyRole("TEACHER", "ADMIN")
                        // Quét QR — STUDENT (LIVE-06)
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/qr/scan").hasRole("STUDENT")

                        // ── RECORDING endpoints ───────────────────────────────────────────
                        // Yêu cầu ghi hình — TEACHER (LIVE-07)
                        .requestMatchers(HttpMethod.POST, "/api/live-sessions/*/recording/request").hasAnyRole("TEACHER", "ADMIN")
                        // Xem recording — authenticated (LIVE-BR-04: service/class check)
                        .requestMatchers(HttpMethod.GET, "/api/live-sessions/*/recording").authenticated()

                        // ── WEBHOOK endpoints ──────────────────────────────────────────────
                        // Public — xác thực qua HMAC trong WebhookServiceImpl (LIVE-BR-05)
                        .requestMatchers("/api/webhooks/live/**").permitAll()
                        // Backend-to-backend event endpoint validates X-Internal-Token itself.
                        .requestMatchers("/api/internal/events/**").permitAll()

                        // Tất cả request còn lại phải authenticated
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized - Missing or invalid token");
                }))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS config — cho phép frontend (Vite dev server) gọi API.
     * Production nên thu hẹp allowedOrigins về domain thật.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
