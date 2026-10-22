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
public class DepositResponse {
    private String orderCode;
    private BigDecimal amount;
    private String qrUrl;
    private String accountNumber;
    private String accountName;
    private String bankName;
    private String description;
}
