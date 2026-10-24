package com.example.demo.service;

import com.example.demo.dto.request.ClassOrderRequest;
import com.example.demo.dto.response.ClassOrderResponse;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClassOrderService {
    ClassOrderResponse purchaseClass(Integer studentId, ClassOrderRequest request);
    void processSepayOrderSuccess(String orderCode, BigDecimal transferAmount, String referenceCode);

    void cancelPendingOrder(String orderCode);
    void refundClass(Integer studentId, Integer classId);
    Page<ClassOrderResponse> getStudentOrders(Integer studentId, Pageable pageable);
    Page<ClassOrderResponse> getClassOrders(Integer classId, Pageable pageable);
    void releaseMaturedPendingBalances();
}
