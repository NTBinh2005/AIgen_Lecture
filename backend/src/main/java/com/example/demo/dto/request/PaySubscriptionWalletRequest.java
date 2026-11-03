package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaySubscriptionWalletRequest {
    @NotNull(message = "ID gói dịch vụ không được để trống")
    private Integer planId;
}
