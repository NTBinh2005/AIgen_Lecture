package com.example.demo.controller;

import com.example.demo.dto.response.AuditLogResponse;
import com.example.demo.entity.AuditAction;
import com.example.demo.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit", description = "Audit log inspection APIs for tracking actions (Admin only)")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @Operation(summary = "Get audit logs with optional filters (Admin only)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getLogs(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String resourceType) {
        return ResponseEntity.ok(auditService.getLogs(userId, action, resourceType));
    }
}
