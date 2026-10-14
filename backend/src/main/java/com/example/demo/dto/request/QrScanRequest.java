package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * LIVE-06: Student quét QR để ghi nhận điểm danh offline.
 * LIVE-AC-03: code hết hạn hoặc thuộc buổi khác → 400.
 */
public record QrScanRequest(

        @NotBlank(message = "code không được để trống")
        String code
) {}
