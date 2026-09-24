package com.example.demo.service;

import com.example.demo.dto.request.ScheduleCreateRequest;
import com.example.demo.dto.request.ScheduleUpdateRequest;
import com.example.demo.dto.response.ScheduleResponse;
import java.util.List;

public interface ScheduleService {
    List<ScheduleResponse> findVisible(Integer currentUserId);

    ScheduleResponse findById(Long scheduleId, Integer currentUserId);

    List<ScheduleResponse> findByClass(Integer classId, Integer currentUserId);

    ScheduleResponse create(ScheduleCreateRequest request, Integer currentUserId);

    ScheduleResponse update(Long scheduleId, ScheduleUpdateRequest request, Integer currentUserId);

    void delete(Long scheduleId, Integer currentUserId);
}
