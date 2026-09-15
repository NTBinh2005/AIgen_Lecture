package com.example.demo.controller;

import com.example.demo.dto.response.StatisticsChartsResponse;
import com.example.demo.dto.response.StatisticsOverviewResponse;
import com.example.demo.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller cho tính năng Thống kê & Phân tích (Admin only).
 *
 * Endpoints:
 *   GET /api/statistics/overview  → KPI cards (tổng users, lectures, cost...)
 *   GET /api/statistics/charts    → Series data cho biểu đồ
 */
@Tag(name = "Statistics", description = "API thống kê & phân tích hệ thống (Admin only)")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(
        summary = "Lấy dữ liệu tổng quan KPI",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StatisticsOverviewResponse> getOverview() {
        return ResponseEntity.ok(statisticsService.getOverview());
    }

    @Operation(
        summary = "Lấy dữ liệu biểu đồ (user growth, lecture growth, interactions, role distribution)",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/charts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StatisticsChartsResponse> getCharts() {
        return ResponseEntity.ok(statisticsService.getCharts());
    }
}
