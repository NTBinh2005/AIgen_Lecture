package com.example.demo.dto.response;

import com.example.demo.entity.ExportStatus;
import com.example.demo.entity.ExportType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExportJobResponse {
    private Long jobId;
    private ExportType type;
    private ExportStatus status;
    private Integer progress;
    private String fileUrl;
    private LocalDateTime expiresAt;
    private String errorReport;
    private LocalDateTime createdAt;
}
