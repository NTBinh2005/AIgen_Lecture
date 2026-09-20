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
                        // Self-enroll — STUDENT
                        .requestMatchers(HttpMethod.POST, "/api/classes/self-enroll").hasRole("STUDENT")
                        // Update/Hủy enrollment — TEACHER hoặc ADMIN
                        .requestMatchers(HttpMethod.PATCH, "/api/classes/*/students/*").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/classes/*/students/*").hasAnyRole("TEACHER", "ADMIN")

                        // ── LECTURE endpoints ─────────────────────────────────────────────
                        // Teacher-only: tạo, xóa bài giảng
                        .requestMatchers(HttpMethod.POST, "/api/lectures").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/lectures/**").hasRole("TEACHER")
                        
                        // ── QUIZ MODULE endpoints ─────────────────────────────────────────
                        .requestMatchers("/api/quizzes/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/api/quiz-assignments/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/api/quiz-assignments/student/**").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/*/progress").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/*/preview").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/*/start").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/*/my-result").hasRole("STUDENT")
                        .requestMatchers("/api/attempts/**").authenticated()
                        .requestMatchers("/api/exports/**").hasAnyRole("TEACHER", "ADMIN")

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
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
