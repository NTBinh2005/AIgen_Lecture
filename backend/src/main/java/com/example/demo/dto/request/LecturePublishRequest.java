package com.example.demo.dto.request;

import java.util.UUID;

/** Null versionId publishes the current draft/ready version. */
public record LecturePublishRequest(UUID versionId) {
}
