package com.example.demo.service;

import com.example.demo.dto.request.RefundCreateRequest;
import com.example.demo.dto.response.RefundResponse;
import java.util.List;
import com.example.demo.entity.RefundStatus;

public interface RefundService {
    RefundResponse requestRefund(Integer userId, RefundCreateRequest request);
    RefundResponse getRefundById(Integer userId, Integer refundId);
    List<RefundResponse> getMyRefunds(Integer userId);
    List<RefundResponse> getAllRefunds();
    RefundResponse updateStatus(Integer adminId, Integer refundId, RefundStatus status);
}
