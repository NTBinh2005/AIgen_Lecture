package com.example.demo.repository;

import com.example.demo.entity.Refund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Integer> {

    /** All refunds belonging to a user — for GET /my-refunds */
    List<Refund> findByUserUserId(Integer userId);

    /** Duplicate-refund guard: a payment may only have one refund request */
    boolean existsByPaymentPaymentId(Integer paymentId);
}
