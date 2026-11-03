package com.example.demo.service;

import com.example.demo.dto.request.SepayWebhookRequest;
import com.example.demo.dto.request.WithdrawalCreateRequest;
import com.example.demo.dto.request.WithdrawalReviewRequest;
import com.example.demo.dto.response.WithdrawalResponse;
import com.example.demo.entity.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WithdrawalService {
    WithdrawalResponse requestWithdrawal(Integer teacherId, WithdrawalCreateRequest request);
    Page<WithdrawalResponse> getMyWithdrawals(Integer teacherId, Pageable pageable);
    Page<WithdrawalResponse> getAllWithdrawals(WithdrawalStatus status, Pageable pageable);
    WithdrawalResponse reviewWithdrawal(Integer adminId, Integer withdrawalId, WithdrawalReviewRequest request);
    void handleOutgoingSepayWebhook(SepayWebhookRequest webhook, String rawBody);
}
