package com.example.demo.controller;

import com.example.demo.dto.request.PaymentSucceededRequest;
import com.example.demo.dto.response.EnrollmentResponse;
import com.example.demo.service.PaymentEnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/events")
@RequiredArgsConstructor
public class InternalEventController {
    private final PaymentEnrollmentService paymentEnrollmentService;

    @PostMapping("/payment-succeeded")
    public EnrollmentResponse paymentSucceeded(
            @Valid @RequestBody PaymentSucceededRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {
        return paymentEnrollmentService.handlePaymentSucceeded(request, internalToken);
    }
}
