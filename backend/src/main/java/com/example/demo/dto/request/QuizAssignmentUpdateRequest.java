package com.example.demo.dto.request;
import java.time.LocalDateTime;
import com.example.demo.entity.ResultPolicy;
import jakarta.validation.constraints.Min;
public class QuizAssignmentUpdateRequest {
    private LocalDateTime openAt, closeAt;
    private boolean openAtSpecified, closeAtSpecified;
    @Min(1) private Integer durationMinutes;
    @Min(1) private Integer maxAttempts;
    private ResultPolicy resultPolicy;
    private com.example.demo.entity.AssignmentStatus status;
    public LocalDateTime getOpenAt() { return openAt; }
    public LocalDateTime getCloseAt() { return closeAt; }
    public boolean openAtSpecified() { return openAtSpecified; }
    public boolean closeAtSpecified() { return closeAtSpecified; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public com.example.demo.entity.AssignmentStatus getStatus() { return status; }
    public void setStatus(com.example.demo.entity.AssignmentStatus v) { status = v; }
    public ResultPolicy getResultPolicy() { return resultPolicy; }
    public void setOpenAt(LocalDateTime v) { openAt = v; openAtSpecified = true; }
    public void setCloseAt(LocalDateTime v) { closeAt = v; closeAtSpecified = true; }
    public void setDurationMinutes(Integer v) { durationMinutes = v; }
    public void setMaxAttempts(Integer v) { maxAttempts = v; }
    public void setResultPolicy(ResultPolicy v) { resultPolicy = v; }
}
