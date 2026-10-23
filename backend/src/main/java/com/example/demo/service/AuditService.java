package com.example.demo.service;

import com.example.demo.dto.response.AuditLogResponse;
import com.example.demo.entity.AuditAction;
import java.util.List;

public interface AuditService {
    void log(Integer userId, AuditAction action, String resourceType, String resourceId, String metadata);
    org.springframework.data.domain.Page<AuditLogResponse> getLogs(
            Integer userId, AuditAction action, String resourceType,
            org.springframework.data.domain.Pageable pageable);
}
