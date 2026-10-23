package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WithdrawalReviewRequest {
    @NotBlank(message = "Trạng thái phê duyệt không được để trống")
    private String status; // "APPROVED" | "REJECTED" | "COMPLETED"

    private String adminNote;
}
