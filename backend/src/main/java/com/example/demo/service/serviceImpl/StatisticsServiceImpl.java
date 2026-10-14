package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.StatisticsChartsResponse;
import com.example.demo.dto.response.StatisticsChartsResponse.*;
import com.example.demo.dto.response.StatisticsOverviewResponse;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.InteractionLogRepository;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service cung cấp dữ liệu thống kê cho Admin Dashboard.
 *
 * Hiện tại đang trả về dữ liệu mock để kịp tiến độ demo.
 * TODO: Thay thế từng phần bằng query thực từ Repository sau demo.
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final InteractionLogRepository interactionLogRepository;

    /**
     * Tổng quan KPI — dùng cho các thẻ KPI ở đầu trang thống kê.
     */
    public StatisticsOverviewResponse getOverview() {
        // --- LIVE QUERIES (có thể dùng ngay) ---
        long totalUsers = userRepository.count();
        long totalStudents = userRepository.countByRole(UserRole.STUDENT);
        long totalTeachers = userRepository.countByRole(UserRole.TEACHER);
        long totalLectures = lectureRepository.count();

        // --- LIVE QUERIES (có thể dùng ngay) ---
        long totalAiGeneratedLectures = 0; // Thay bằng query thực nếu có
        long totalInteractions = interactionLogRepository.count();
        double llmCostUsd = 0.0;
        String serverUptime = "100%";

        return new StatisticsOverviewResponse(
                totalUsers,
                totalStudents,
                totalTeachers,
                totalLectures,
                totalAiGeneratedLectures,
                totalInteractions,
                llmCostUsd,
                serverUptime
        );
    }

    /**
     * Dữ liệu biểu đồ — dùng cho các charts trong trang thống kê.
     * Hiện tại là mock data cho demo; replace bằng native query sau.
     */
    public StatisticsChartsResponse getCharts() {
        long studentCount = userRepository.countByRole(UserRole.STUDENT);
        long teacherCount = userRepository.countByRole(UserRole.TEACHER);
        long adminCount = userRepository.countByRole(UserRole.ADMIN);

        List<RoleDistribution> roleDistribution = List.of(
                new RoleDistribution("Học sinh", (int) studentCount, "#10b981"),
                new RoleDistribution("Giáo viên", (int) teacherCount, "#8b5cf6"),
                new RoleDistribution("Admin", (int) adminCount, "#f59e0b")
        );

        // FE is calculating these currently, return empty for now to avoid fake data
        List<MonthlyUserGrowth> userGrowth = List.of();
        List<MonthlyLectureCount> lectureGrowth = List.of();
        List<WeeklyInteraction> weeklyInteractions = List.of();

        return new StatisticsChartsResponse(userGrowth, lectureGrowth, weeklyInteractions, roleDistribution);
    }
}
