package com.example.demo.dto.request;

import com.example.demo.entity.LectureAccessScope;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LectureUpdateRequest {
    @Size(max = 255, message = "Title must be less than 255 characters")
    private String title;

    @Size(max = 2_000_000, message = "Content is too large")
    private String content;

    private LectureAccessScope accessScope;
}
