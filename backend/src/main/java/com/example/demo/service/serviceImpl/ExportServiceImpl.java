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
    private final com.example.demo.repository.AttemptRepository attemptRepository;
    private final com.example.demo.repository.AttemptAnswerRepository answerRepository;
    
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
        job.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(retentionDays));
        
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
    @Transactional(readOnly = true)
    public Resource downloadFile(Integer userId, Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getRequestedBy().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        try {
            ExportRequest scope = objectMapper.readValue(job.getScopeJson(), ExportRequest.class);
            ClassEntity target = scope.getAssignmentId() != null
                    ? assignmentRepository.findById(scope.getAssignmentId()).orElseThrow().getClassEntity()
                    : classRepository.findById(scope.getClassId()).orElseThrow();
            classAccessService.assertCanManage(target, userId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể đọc phạm vi tệp xuất", ex);
        }
                
        if (job.getExpiresAt() != null && !job.getExpiresAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw new ResourceNotFoundException("Tệp xuất đã hết hạn");
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
    public org.springframework.data.domain.Page<ExportJobResponse> getExportJobs(
            Integer userId, org.springframework.data.domain.Pageable pageable) {
        return exportJobRepository.findByRequestedBy_UserId(userId, pageable).map(this::mapToResponse);
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
        ExportJob job = exportJobRepository.lockByJobId(jobId)
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

    private List<com.example.demo.entity.Attempt> exportAttempts(ExportJob job) throws IOException {
        ExportRequest scope = objectMapper.readValue(job.getScopeJson(), ExportRequest.class);
        List<QuizAssignment> assignments;
        if (scope.getAssignmentId() != null) {
            assignments = List.of(assignmentRepository.findById(scope.getAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài giao")));
        } else {
            ClassEntity target = classRepository.findById(scope.getClassId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp"));
            classAccessService.assertCanManage(target, job.getRequestedBy().getUserId());
            assignments = assignmentRepository.findByClassEntity_ClassId(scope.getClassId());
        }
        List<com.example.demo.entity.Attempt> attempts = new java.util.ArrayList<>();
        for (QuizAssignment assignment : assignments) {
            classAccessService.assertCanManage(assignment.getClassEntity(), job.getRequestedBy().getUserId());
            attempts.addAll(attemptRepository.findByAssignment_AssignmentId(assignment.getAssignmentId()));
        }
        attempts.sort(java.util.Comparator.comparing(com.example.demo.entity.Attempt::getAttemptId));
        return attempts;
    }

    private void writeWorkbook(File file, ExportJob job) throws IOException {
        List<com.example.demo.entity.Attempt> attempts = exportAttempts(job);
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(file)) {
            Sheet results = workbook.createSheet("Results");
            writeRow(results, 0, new Object[]{"Student ID", "Student name", "Email", "Class", "Quiz", "Assignment ID",
                    "Attempt ID", "Attempt no", "Status", "Objective score", "Final score", "Submitted at (UTC)"});
            int index = 1;
            Sheet answers = workbook.createSheet("Answers");
            writeRow(answers, 0, new Object[]{"Attempt ID", "Student", "Question ID", "Response", "Correct", "Points", "Teacher score", "AI suggested score"});
            int answerIndex = 1;
            for (com.example.demo.entity.Attempt attempt : attempts) {
                writeRow(results, index++, new Object[]{attempt.getStudent().getUserId(), attempt.getStudent().getName(),
                        attempt.getStudent().getEmail(), attempt.getAssignment().getClassEntity().getClassName(),
                        attempt.getQuizVersion().getQuiz().getTitle(), attempt.getAssignment().getAssignmentId(),
                        attempt.getAttemptId(), attempt.getAttemptNo(), attempt.getStatus(), attempt.getObjectiveScore(),
                        attempt.getFinalScore(), attempt.getSubmittedAt()});
                for (com.example.demo.entity.AttemptAnswer answer : answerRepository.findByAttempt_AttemptId(attempt.getAttemptId())) {
                    writeRow(answers, answerIndex++, new Object[]{attempt.getAttemptId(), attempt.getStudent().getName(),
                            answer.getQuestionId(), answer.getResponse(), answer.getIsCorrect(), answer.getPointsAwarded(),
                            answer.getTeacherFinalScore(), answer.getAiSuggestedScore()});
                }
            }
            for (Sheet sheet : List.of(results, answers)) {
                sheet.createFreezePane(0, 1);
                for (int col = 0; col < sheet.getRow(0).getLastCellNum(); col++) sheet.setColumnWidth(col, 24 * 256);
            }
            workbook.write(fos);
        }
    }

    private void writeRow(Sheet sheet, int index, Object[] values) {
        Row row = sheet.createRow(index);
        for (int col = 0; col < values.length; col++) {
            Cell cell = row.createCell(col);
            Object value = values[col];
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (value != null) {
                String text = neutralizeFormula(value.toString());
                cell.setCellValue(text.length() > 32767 ? text.substring(0, 32767) : text);
            }
        }
    }

    private void writePdf(File file, ExportJob job) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("STUDENT RESULTS (timestamps in UTC)");
        for (com.example.demo.entity.Attempt attempt : exportAttempts(job)) {
            lines.add("Student: " + attempt.getStudent().getName() + " (" + attempt.getStudent().getEmail() + ")");
            lines.add("Class: " + attempt.getAssignment().getClassEntity().getClassName()
                    + " / Quiz: " + attempt.getQuizVersion().getQuiz().getTitle());
            lines.add("Attempt: " + attempt.getAttemptId() + " / Status: " + attempt.getStatus()
                    + " / Score: " + attempt.getFinalScore() + " / Submitted: " + attempt.getSubmittedAt());
            for (com.example.demo.entity.AttemptAnswer answer : answerRepository.findByAttempt_AttemptId(attempt.getAttemptId())) {
                lines.add("Question " + answer.getQuestionId() + ": " + answer.getResponse());
                lines.add("Points: " + answer.getPointsAwarded() + " / Teacher score: " + answer.getTeacherFinalScore());
            }
            lines.add("");
        }
        if (lines.size() == 1) lines.add("No attempts in this scope.");
        try (PDDocument document = new PDDocument();
             InputStream fontBytes = new org.springframework.core.io.ClassPathResource("fonts/NotoSans-Regular.ttf").getInputStream()) {
            var font = org.apache.pdfbox.pdmodel.font.PDType0Font.load(document, fontBytes);
            List<String> wrapped = new java.util.ArrayList<>();
            for (String line : lines) {
                StringBuilder current = new StringBuilder();
                for (int point : line.codePoints().toArray()) {
                    String character = Character.isISOControl(point) ? " " : new String(Character.toChars(point));
                    try { font.encode(character); }
                    catch (IllegalArgumentException unsupportedGlyph) { character = "?"; }
                    if (!current.isEmpty() && font.getStringWidth(current + character) * 10 / 1000 > 500) {
                        wrapped.add(current.toString()); current.setLength(0);
                    }
                    current.append(character);
                }
                wrapped.add(current.toString());
            }
            for (int offset = 0; offset < wrapped.size(); offset += 45) {
                PDPage page = new PDPage(); document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText(); content.setFont(font, 10);
                    content.newLineAtOffset(50, 740); content.setLeading(15);
                    for (int line = offset; line < Math.min(offset + 45, wrapped.size()); line++) {
                        content.showText(wrapped.get(line)); content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(file);
        }
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
