package com.example.demo.repository;

import com.example.demo.entity.WithdrawalRequest;
import com.example.demo.entity.WithdrawalStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Integer> {
    Page<WithdrawalRequest> findByTeacherUserIdOrderByCreatedAtDesc(Integer teacherId, Pageable pageable);
    Page<WithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status, Pageable pageable);
    Optional<WithdrawalRequest> findBySepayWebhookId(Long sepayWebhookId);
}
