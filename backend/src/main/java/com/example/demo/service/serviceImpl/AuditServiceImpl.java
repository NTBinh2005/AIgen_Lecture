package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.AuditLogResponse;
import com.example.demo.entity.AuditAction;
import com.example.demo.entity.AuditLog;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Integer userId, AuditAction action, String resourceType, String resourceId, String metadata) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setMetadata(metadata);

            HttpServletRequest request = getCurrentHttpRequest();
            if (request != null) {
                auditLog.setIpAddress(extractClientIp(request));
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 500) {
                    userAgent = userAgent.substring(0, 500);
                }
                auditLog.setUserAgent(userAgent);
            }

            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Failed to write audit log for action: {}", action, ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLogs(Integer userId, AuditAction action, String resourceType) {
        return auditLogRepository.searchLogs(userId, action, resourceType).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getMetadata(),
                log.getCreatedAt()
        );
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
