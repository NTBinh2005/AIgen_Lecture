package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.response.DepositResponse;
import com.example.demo.dto.response.WalletResponse;
import com.example.demo.dto.response.WalletTransactionResponse;
import com.example.demo.entity.Wallet;
import com.example.demo.entity.WalletTransactionType;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletService {
    WalletResponse getMyWallet(Integer userId);
    Wallet getOrCreateWallet(Integer userId);
    Page<WalletTransactionResponse> getMyTransactions(Integer userId, Pageable pageable);
    DepositResponse createDeposit(Integer userId, DepositRequest request);
    void processDepositSuccess(String orderCode, BigDecimal amount, String referenceId);
    void paySubscription(Integer userId, Integer planId);

    void credit(Wallet wallet, BigDecimal amount, WalletTransactionType type, String description, String refId);
    void debit(Wallet wallet, BigDecimal amount, WalletTransactionType type, String description, String refId);
    void creditPending(Wallet wallet, BigDecimal amount, String description, String refId);
    void releasePendingToAvailable(Wallet wallet, BigDecimal amount, String description, String refId);
    void deductPending(Wallet wallet, BigDecimal amount, WalletTransactionType type, String description, String refId);
    void lockBalance(Wallet wallet, BigDecimal amount, String description, String refId);
    void unlockBalance(Wallet wallet, BigDecimal amount, String description, String refId);
    void deductLockedBalance(Wallet wallet, BigDecimal amount, String description, String refId);
}
