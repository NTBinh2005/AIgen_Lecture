package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body khi Student nộp câu trả lời cho một câu hỏi.
 * POST /api/interactions
 */
@Getter
@Setter
public class SubmitAnswerRequest {

    @NotNull(message = "elementId không được để trống")
    private Long elementId;

    @NotBlank(message = "submittedAnswer không được để trống")
    @Size(min = 1, max = 1, message = "submittedAnswer phải là 1 ký tự (A/B/C/D)")
    private String submittedAnswer;

    /**
     * Thời gian học sinh đọc và suy nghĩ (giây).
     * Optional — null nếu không đo được.
     */
    private Integer timeSpent;
}
