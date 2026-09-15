package com.example.demo.service;

import com.example.demo.dto.response.StatisticsChartsResponse;
import com.example.demo.dto.response.StatisticsOverviewResponse;

public interface StatisticsService {

    StatisticsOverviewResponse getOverview();

    StatisticsChartsResponse getCharts();
}
