package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ENRL-06: Student tự ghi danh bằng mã lớp.
 */
public record SelfEnrollRequest(
        @NotBlank @Size(max = 20) String classCode
) {
}
