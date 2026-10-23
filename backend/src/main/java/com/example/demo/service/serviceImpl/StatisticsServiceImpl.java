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
 * Counts and bounded chart series are aggregated from persisted records.
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final com.example.demo.repository.LectureVersionRepository lectureVersionRepository;

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
        long totalAiGeneratedLectures = lectureVersionRepository.countAiGeneratedLectures();
        long totalInteractions = interactionLogRepository.count();
        Double llmCostUsd = null; // No billing telemetry; never report an invented zero.
        String serverUptime = java.time.Duration.ofMillis(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()).toString();

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
     * Aggregate the past 12 months and 7 UTC days.
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

        java.time.YearMonth current = java.time.YearMonth.now(java.time.ZoneOffset.UTC);
        java.time.LocalDateTime since = current.minusMonths(11).atDay(1).atStartOfDay();
        var userRows = userRepository.monthlyGrowth(since);
        var lectureRows = lectureRepository.monthlyGrowth(since);
        var aiRows = lectureVersionRepository.monthlyAiGrowth(since);
        List<MonthlyUserGrowth> userGrowth = new java.util.ArrayList<>();
        List<MonthlyLectureCount> lectureGrowth = new java.util.ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            java.time.YearMonth month = current.minusMonths(i);
            long students = 0, teachers = 0, lectures = 0, aiGenerated = 0;
            for (Object[] row : userRows) if (((Number) row[0]).intValue() == month.getYear() && ((Number) row[1]).intValue() == month.getMonthValue()) {
                if (row[2] == UserRole.STUDENT) students = ((Number) row[3]).longValue();
                if (row[2] == UserRole.TEACHER) teachers = ((Number) row[3]).longValue();
            }
            for (Object[] row : lectureRows) if (((Number) row[0]).intValue() == month.getYear() && ((Number) row[1]).intValue() == month.getMonthValue()) lectures = ((Number) row[2]).longValue();
            for (Object[] row : aiRows) if (((Number) row[0]).intValue() == month.getYear() && ((Number) row[1]).intValue() == month.getMonthValue()) aiGenerated = ((Number) row[2]).longValue();
            userGrowth.add(new MonthlyUserGrowth(month.toString(), students, teachers));
            lectureGrowth.add(new MonthlyLectureCount(month.toString(), lectures, aiGenerated));
        }
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        var interactionRows = interactionLogRepository.dailyCounts(today.minusDays(6).atStartOfDay());
        List<WeeklyInteraction> weeklyInteractions = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i); long quizzes = 0;
            for (Object[] row : interactionRows) if (((Number) row[0]).intValue() == day.getYear() && ((Number) row[1]).intValue() == day.getMonthValue() && ((Number) row[2]).intValue() == day.getDayOfMonth()) quizzes = ((Number) row[3]).longValue();
            weeklyInteractions.add(new WeeklyInteraction(day.toString(), quizzes, 0, 0));
        }

        return new StatisticsChartsResponse(userGrowth, lectureGrowth, weeklyInteractions, roleDistribution);
    }
}
