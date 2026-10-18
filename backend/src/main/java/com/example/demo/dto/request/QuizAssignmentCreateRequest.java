package com.example.demo.dto.request;

import com.example.demo.entity.ResultPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuizAssignmentCreateRequest {
    @NotNull(message = "Mã phiên bản bài kiểm tra không được để trống")
    private Long quizVersionId;

    @NotNull(message = "Mã lớp không được để trống")
    private Integer classId;

    private LocalDateTime openAt;
    
    private LocalDateTime closeAt;
    
    @jakarta.validation.constraints.Min(1)
    private Integer durationMinutes;
    
    @jakarta.validation.constraints.Min(1)
    private Integer maxAttempts;
    
    @NotNull(message = "Chính sách hiển thị kết quả không được để trống")
    private ResultPolicy resultPolicy;
    
    private Integer shuffleSeedBase;
}
