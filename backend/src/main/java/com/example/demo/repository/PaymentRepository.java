package com.example.demo.repository;

import com.example.demo.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.paymentId = :id")
    Optional<Payment> lockByPaymentId(@org.springframework.data.repository.query.Param("id") Integer id);
    Optional<Payment> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.transactionId = :transactionId")
    Optional<Payment> lockByTransactionId(@org.springframework.data.repository.query.Param("transactionId") String transactionId);
    List<Payment> findByUserUserId(Integer userId);
    List<Payment> findAllByOrderByCreatedAtDesc();
    org.springframework.data.domain.Page<Payment> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select p from Payment p
            where p.status = com.example.demo.entity.PaymentStatus.PENDING
              and p.createdAt < :cutoff
            """)
    List<Payment> lockPendingCreatedBefore(
            @org.springframework.data.repository.query.Param("cutoff") Instant cutoff);
}
