package com.example.demo.controller;

import com.example.demo.dto.request.ScheduleCreateRequest;
import com.example.demo.dto.request.ScheduleUpdateRequest;
import com.example.demo.dto.response.ScheduleResponse;
import com.example.demo.service.ScheduleService;
import com.example.demo.service.serviceImpl.ScheduleServiceImpl;
import com.example.demo.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/schedules")
@Tag(name = "Schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleServiceImpl scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @Operation(summary = "List all schedules")
    public List<ScheduleResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.findVisible(principal.getUserId());
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "Get schedule by id")
    public ScheduleResponse findById(@PathVariable Long scheduleId,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.findById(scheduleId, principal.getUserId());
    }

    @GetMapping("/class/{classId}")
    @Operation(summary = "Get schedules by class id")
    public List<ScheduleResponse> findByClass(@PathVariable Integer classId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.findByClass(classId, principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create schedule")
    public ScheduleResponse create(@Valid @RequestBody ScheduleCreateRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.create(request, principal.getUserId());
    }

    @PatchMapping("/{scheduleId}")
    @Operation(summary = "Update schedule")
    public ScheduleResponse update(
            @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return scheduleService.update(scheduleId, request, principal.getUserId());
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete schedule")
    public void delete(@PathVariable Long scheduleId,
                       @AuthenticationPrincipal UserPrincipal principal) {
        scheduleService.delete(scheduleId, principal.getUserId());
    }
}
