package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

public record LectureCollaboratorRequest(@NotNull Integer userId) {
}
