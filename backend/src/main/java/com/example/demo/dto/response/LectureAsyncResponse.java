package com.example.demo.dto.response;

import java.util.UUID;

public record LectureAsyncResponse(Long lectureId, UUID lectureBusinessId, UUID jobId) {
}
