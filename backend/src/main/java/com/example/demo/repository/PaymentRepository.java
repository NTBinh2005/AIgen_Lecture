package com.example.demo.repository;

import com.example.demo.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    Optional<Payment> findByTransactionId(String transactionId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.transactionId = :transactionId")
    Optional<Payment> lockByTransactionId(@org.springframework.data.repository.query.Param("transactionId") String transactionId);
    List<Payment> findByUserUserId(Integer userId);
    List<Payment> findAllByOrderByCreatedAtDesc();
}
