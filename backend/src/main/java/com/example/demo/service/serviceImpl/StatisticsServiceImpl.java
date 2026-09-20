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

        // --- MOCK DATA (thay bằng query thực sau demo) ---
        long totalAiGeneratedLectures = Math.round(totalLectures * 0.95); // ~95% là AI-generated
        long totalInteractions = 24_680L;
        double llmCostUsd = 142.50;
        String serverUptime = "99.9%";

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
        // Mock: Tăng trưởng người dùng theo tháng
        List<MonthlyUserGrowth> userGrowth = List.of(
                new MonthlyUserGrowth("T1", 82, 12),
                new MonthlyUserGrowth("T2", 120, 18),
                new MonthlyUserGrowth("T3", 155, 22),
                new MonthlyUserGrowth("T4", 190, 25),
                new MonthlyUserGrowth("T5", 248, 31),
                new MonthlyUserGrowth("T6", 310, 38),
                new MonthlyUserGrowth("T7", 405, 44),
                new MonthlyUserGrowth("T8", 520, 52),
                new MonthlyUserGrowth("T9", 680, 60),
                new MonthlyUserGrowth("T10", 820, 71),
                new MonthlyUserGrowth("T11", 980, 85),
                new MonthlyUserGrowth("T12", 1160, 98)
        );

        // Mock: Bài giảng được tạo theo tháng
        List<MonthlyLectureCount> lectureGrowth = List.of(
                new MonthlyLectureCount("T1", 15, 10),
                new MonthlyLectureCount("T2", 28, 22),
                new MonthlyLectureCount("T3", 42, 38),
                new MonthlyLectureCount("T4", 60, 55),
                new MonthlyLectureCount("T5", 88, 82),
                new MonthlyLectureCount("T6", 110, 105),
                new MonthlyLectureCount("T7", 145, 140),
                new MonthlyLectureCount("T8", 180, 172),
                new MonthlyLectureCount("T9", 225, 218),
                new MonthlyLectureCount("T10", 270, 264),
                new MonthlyLectureCount("T11", 320, 312),
                new MonthlyLectureCount("T12", 380, 370)
        );

        // Mock: Tương tác trong tuần
        List<WeeklyInteraction> weeklyInteractions = List.of(
                new WeeklyInteraction("T2", 120, 85, 45),
                new WeeklyInteraction("T3", 185, 120, 60),
                new WeeklyInteraction("T4", 145, 95, 55),
                new WeeklyInteraction("T5", 220, 160, 72),
                new WeeklyInteraction("T6", 198, 145, 80),
                new WeeklyInteraction("T7", 260, 190, 95),
                new WeeklyInteraction("CN", 170, 110, 50)
        );

        // Mock: Phân bố vai trò
        List<RoleDistribution> roleDistribution = List.of(
                new RoleDistribution("Học sinh", 1160, "#10b981"),
                new RoleDistribution("Giáo viên", 98, "#8b5cf6"),
                new RoleDistribution("Admin", 5, "#f59e0b")
        );

        return new StatisticsChartsResponse(userGrowth, lectureGrowth, weeklyInteractions, roleDistribution);
    }
}
