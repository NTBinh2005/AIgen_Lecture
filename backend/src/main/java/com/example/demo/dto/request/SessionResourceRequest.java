package com.example.demo.dto.request;

import com.example.demo.entity.SessionResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SessionResourceRequest(
        @NotNull SessionResourceType resourceType,
        @NotNull java.util.UUID resourceId,
        @Size(max = 255) String title,
        boolean downloadAllowed
) {}
