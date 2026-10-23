package com.example.demo.dto.request;

import com.example.demo.entity.LectureAccessScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class LectureUpdateRequest {
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @Size(max = 2_000_000, message = "Nội dung quá lớn")
    private String content;

    private LectureAccessScope accessScope;

    /**
     * Cho phép giáo viên sửa slide do AI tạo. Khi có giá trị, slide được ghi vào
     * {@code slideContent} của version nháp hiện tại (không tạo bài giảng mới).
     */
    @Valid
    private List<LectureCreateRequest.SlideDto> slides;
}
