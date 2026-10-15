package com.example.demo.repository;

import com.example.demo.entity.Refund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Integer> {
    @org.springframework.data.jpa.repository.Query("select r.payment.paymentId from Refund r where r.refundId = :id")
    java.util.Optional<Integer> findPaymentIdByRefundId(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Refund r where r.refundId = :id")
    java.util.Optional<Refund> lockByRefundId(@org.springframework.data.repository.query.Param("id") Integer id);

    /** All refunds belonging to a user — for GET /my-refunds */
    List<Refund> findByUserUserId(Integer userId);

    /** Duplicate-refund guard: a payment may only have one refund request */
    boolean existsByPaymentPaymentId(Integer paymentId);

    List<Refund> findAllByOrderByCreatedAtDesc();
    org.springframework.data.domain.Page<Refund> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
