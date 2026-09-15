package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

public record ClassLectureRequest(@NotNull Long lectureId) {
}
