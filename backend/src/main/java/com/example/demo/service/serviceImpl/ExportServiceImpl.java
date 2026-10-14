package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.ExportRequest;
import com.example.demo.dto.response.ExportJobResponse;
import com.example.demo.entity.ExportJob;
import com.example.demo.entity.ExportStatus;
import com.example.demo.repository.ExportJobRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import com.example.demo.repository.QuizAssignmentRepository;
import com.example.demo.repository.ClassRepository;
import com.example.demo.service.ClassAccessService;
import com.example.demo.entity.QuizAssignment;
import com.example.demo.entity.ClassEntity;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.entity.ExportType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ExportJobRepository exportJobRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final QuizAssignmentRepository assignmentRepository;
    private final ClassRepository classRepository;
    private final ClassAccessService classAccessService;
    
    @Value("${app.quiz.export.storage-dir:exports}")
    private String exportDir;
    
    @Value("${app.quiz.export.retention-days:7}")
    private int retentionDays;

    @Override
    @Transactional
    public ExportJobResponse requestExport(Integer userId, ExportRequest request) {
        if (request.getAssignmentId() == null && request.getClassId() == null) {
            throw new BadRequestException("assignmentId or classId is required");
        }
        ClassEntity targetClass;
        if (request.getAssignmentId() != null) {
            QuizAssignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
            targetClass = assignment.getClassEntity();
            if (request.getClassId() != null
                    && !request.getClassId().equals(targetClass.getClassId())) {
                throw new BadRequestException("assignmentId does not belong to classId");
            }
        } else {
            targetClass = classRepository.findById(request.getClassId())
                    .orElseThrow(() -> new ResourceNotFoundException("Class not found"));
        }
        classAccessService.assertCanManage(targetClass, userId);
        ExportJob job = new ExportJob();
        job.setType(request.getType());
        job.setRequestedBy(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
        
        try {
            job.setScopeJson(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            job.setScopeJson("{}");
        }
        
        job.setStatus(ExportStatus.QUEUED);
        job.setExpiresAt(LocalDateTime.now().plusDays(retentionDays));
        
        ExportJob saved = exportJobRepository.save(job);
        
        // Return response, Scheduler will pick this up or we can async trigger it here.
        // For simplicity, we just save as QUEUED. QuizScheduler will process it.
        
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportJobResponse getJobStatus(Integer userId, Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
                
        if (!job.getRequestedBy().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        
        return mapToResponse(job);
    }

    @Override
    public Resource downloadFile(Integer userId, Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getRequestedBy().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
                
        if (job.getStatus() != ExportStatus.COMPLETED || job.getFileKey() == null) {
            throw new IllegalStateException("File is not ready for download");
        }
        
        File file = new File(exportDir, job.getFileKey());
        if (!file.exists()) {
            throw new ResourceNotFoundException("File not found on disk");
        }
        
        return new FileSystemResource(file);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExportJobResponse> getExportJobs(Integer userId) {
        return exportJobRepository.findByRequestedBy_UserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToResponse).toList();
    }

    @Override
    public void validateQuizTemplate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException(ErrorCode.TEMPLATE_INVALID.getMessage() + ": Sheet not found");
            }
            
            // Validate specific structure (e.g. Row 0 must be header: Type, Text, Options, Answer, Points)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null || headerRow.getCell(0) == null) {
                throw new IllegalArgumentException(ErrorCode.TEMPLATE_INVALID.getMessage() + ": Missing header row");
            }
            
            // Mock further validation for brevity...
            
        } catch (Exception e) {
            throw new IllegalArgumentException(ErrorCode.TEMPLATE_INVALID.getMessage() + ": " + e.getMessage(), e);
        }
    }
    
    // Method to be called by QuizScheduler
    @Transactional
    public void processExportJob(Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found"));
        if (job.getStatus() != ExportStatus.QUEUED) return;
        
        job.setStatus(ExportStatus.PROCESSING);
        exportJobRepository.save(job);
        
        try {
            // Generate File
            File dir = new File(exportDir);
            if (!dir.exists()) dir.mkdirs();
            
            boolean pdf = job.getType() == ExportType.STUDENT_RESULT_PDF;
            String fileName = UUID.randomUUID() + (pdf ? ".pdf" : ".xlsx");
            File file = new File(dir, fileName);

            if (pdf) {
                writePdf(file, job);
            } else {
                writeWorkbook(file, job);
            }
            
            job.setFileKey(fileName);
            job.setFileUrl("/api/exports/" + jobId + "/download");
            job.setStatus(ExportStatus.COMPLETED);
            job.setProgress(100);
            
        } catch (Exception e) {
            job.setStatus(ExportStatus.FAILED);
            job.setErrorReport(e.getMessage());
            log.error("Export failed", e);
        }
        
        exportJobRepository.save(job);
    }

    private void writeWorkbook(File file, ExportJob job) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Results");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Export type");
            header.createCell(1).setCellValue("Scope");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(job.getType().name());
            data.createCell(1).setCellValue(neutralizeFormula(job.getScopeJson()));
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(fos);
        }
    }

    private void writePdf(File file, ExportJob job) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 18);
                content.newLineAtOffset(72, 740);
                content.showText("STUDENT RESULT");
                content.setLeading(24);
                content.newLine();
                content.setFont(PDType1Font.HELVETICA, 11);
                content.showText("Export job: " + job.getJobId());
                content.newLine();
                content.showText("Scope: " + ascii(job.getScopeJson()));
                content.endText();
            }
            document.save(file);
        }
    }

    private String ascii(String value) {
        String safe = value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
        return safe.length() <= 100 ? safe : safe.substring(0, 100);
    }
    
    private String neutralizeFormula(String value) {
        if (value == null) return "";
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
            return "'" + value;
        }
        return value;
    }

    private ExportJobResponse mapToResponse(ExportJob job) {
        ExportJobResponse res = new ExportJobResponse();
        res.setJobId(job.getJobId());
        res.setType(job.getType());
        res.setStatus(job.getStatus());
        res.setProgress(job.getProgress());
        res.setFileUrl(job.getFileUrl());
        res.setExpiresAt(job.getExpiresAt());
        res.setErrorReport(job.getErrorReport());
        res.setCreatedAt(job.getCreatedAt());
        return res;
    }
}
