package com.example.demo.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record TabSwitchSignalRequest(
    @NotBlank @Size(max = 32) @Pattern(regexp = "TAB_SWITCH") String type
) {}
