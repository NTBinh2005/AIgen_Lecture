package com.example.demo.dto.response;

/**
 * DTO trả về dữ liệu tổng quan thống kê hệ thống.
 * Dùng cho GET /api/statistics/overview
 */
public record StatisticsOverviewResponse(
    long totalUsers,
    long totalStudents,
    long totalTeachers,
    long totalLectures,
    long totalAiGeneratedLectures,
    long totalInteractions,
    double llmCostUsd,
    String serverUptime
) {}
