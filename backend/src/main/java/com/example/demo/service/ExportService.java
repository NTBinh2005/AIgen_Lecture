package com.example.demo.service;

import com.example.demo.dto.request.ExportRequest;
import com.example.demo.dto.response.ExportJobResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ExportService {
    ExportJobResponse requestExport(Integer userId, ExportRequest request);
    ExportJobResponse getJobStatus(Integer userId, Long jobId);
    Resource downloadFile(Integer userId, Long jobId);
    org.springframework.data.domain.Page<ExportJobResponse> getExportJobs(
            Integer userId, org.springframework.data.domain.Pageable pageable);
    
    // Validates a template uploaded by teacher
    void validateQuizTemplate(MultipartFile file);
}
