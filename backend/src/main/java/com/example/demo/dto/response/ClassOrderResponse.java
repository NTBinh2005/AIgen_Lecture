package com.example.demo.dto.response;

import com.example.demo.entity.ClassOrderStatus;
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
public class ClassOrderResponse {
    private Integer orderId;
    private String orderCode;
    private Integer studentId;
    private String studentName;
    private Integer classId;
    private String className;
    private BigDecimal price;
    private BigDecimal platformFeePercent;
    private BigDecimal platformFeeAmount;
    private BigDecimal teacherAmount;
    private String paymentMethod;
    private ClassOrderStatus status;
    private Instant holdUntil;
    private Instant createdAt;
    private Instant refundedAt;
    private String qrUrl;
    private String accountNumber;
    private String accountName;
    private String bankName;
}
