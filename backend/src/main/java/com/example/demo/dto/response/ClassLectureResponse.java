package com.example.demo.dto.response;

import java.time.LocalDateTime;

public record ClassLectureResponse(Long lectureId, String title, LocalDateTime publishedAt, LocalDateTime assignedAt) {
}
