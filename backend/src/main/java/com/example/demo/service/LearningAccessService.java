package com.example.demo.service;

import com.example.demo.dto.response.LearningAccessResponse;
import java.util.List;

public interface LearningAccessService {

    /** Trả về tất cả quyền học của user hiện tại */
    List<LearningAccessResponse> getMyAccess(Integer userId);

    /**
     * Kiểm tra user có quyền truy cập product/course không.
     * Dùng để các service khác query hoặc frontend kiểm tra trước khi hiển thị nội dung.
     */
    boolean hasAccess(Integer userId, Integer productId);
}
