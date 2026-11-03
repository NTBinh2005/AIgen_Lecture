package com.example.demo.dto.response;

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
public class WalletResponse {
    private Integer walletId;
    private Integer userId;
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private BigDecimal lockedBalance;
    private BigDecimal totalBalance;
    private Instant updatedAt;
}
