package com.example.demo.dto.response;

import java.math.BigDecimal;
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
public class ClassProgressResponse {
    private Integer classId;
    private Integer totalLectures;
    private Integer completedLectures;
    private BigDecimal watchedPercentage;
    private Boolean canRefund;
    private String refundBlockReason;
    private Long remainingHoldSeconds;
}
