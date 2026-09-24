package com.example.demo.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkEnrollmentRequest(@NotEmpty List<Integer> studentIds) {}
