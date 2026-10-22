package com.example.demo.dto.response;

import com.example.demo.entity.WithdrawalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalResponse {
    private Integer withdrawalId;
    private Integer teacherId;
    private String teacherName;
    private String teacherEmail;
    private BigDecimal amount;
    private String bankName;
    private String bankAccount;
    private String accountHolder;
    private WithdrawalStatus status;
    private String adminNote;
    private String qrUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
