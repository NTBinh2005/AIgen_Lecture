package com.example.demo.dto.response;

import java.util.List;

/**
 * DTO chứa các series data để vẽ biểu đồ trên frontend.
 * Dùng cho GET /api/statistics/charts
 */
public record StatisticsChartsResponse(
    List<MonthlyUserGrowth> userGrowth,
    List<MonthlyLectureCount> lectureGrowth,
    List<WeeklyInteraction> weeklyInteractions,
    List<RoleDistribution> roleDistribution
) {

    public record MonthlyUserGrowth(String month, long students, long teachers) {}

    public record MonthlyLectureCount(String month, long lectures, long aiGenerated) {}

    public record WeeklyInteraction(String day, long quizzes, long flashcards, long notes) {}

    public record RoleDistribution(String name, long value, String color) {}
}
