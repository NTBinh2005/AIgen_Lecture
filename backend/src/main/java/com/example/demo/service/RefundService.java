package com.example.demo.service;

import com.example.demo.dto.request.RefundCreateRequest;
import com.example.demo.dto.response.RefundResponse;
import java.util.List;

public interface RefundService {
    RefundResponse requestRefund(Integer userId, RefundCreateRequest request);
    RefundResponse getRefundById(Integer userId, Integer refundId);
    List<RefundResponse> getMyRefunds(Integer userId);
}
