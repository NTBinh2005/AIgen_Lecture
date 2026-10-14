package com.example.demo.repository;
import com.example.demo.entity.SubscriptionCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SubscriptionCreditRepository extends JpaRepository<SubscriptionCredit, Integer> {
    List<SubscriptionCredit> findBySubscriptionSubscriptionIdAndRefundedFalse(Long id);
}
