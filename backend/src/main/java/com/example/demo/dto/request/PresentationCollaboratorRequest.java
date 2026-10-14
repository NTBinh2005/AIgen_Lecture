package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

public record PresentationCollaboratorRequest(@NotNull Integer userId) {
}
