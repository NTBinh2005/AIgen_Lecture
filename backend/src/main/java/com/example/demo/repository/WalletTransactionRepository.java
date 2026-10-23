package com.example.demo.repository;

import com.example.demo.entity.WalletTransaction;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Integer> {
    Page<WalletTransaction> findByWalletWalletIdOrderByCreatedAtDesc(Integer walletId, Pageable pageable);
    List<WalletTransaction> findByWalletWalletIdOrderByCreatedAtDesc(Integer walletId);
}
