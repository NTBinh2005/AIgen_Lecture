package com.example.demo.repository;

import com.example.demo.entity.LearningAccess;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LearningAccessRepository extends JpaRepository<LearningAccess, Integer> {

    /** Tất cả quyền học của một user */
    List<LearningAccess> findByUserUserId(Integer userId);

    /** Kiểm tra user có quyền với product này không */
    boolean existsByUserUserIdAndProductId(Integer userId, Integer productId);

    /** Tránh cấp quyền trùng lặp cho cùng payment */
    boolean existsByPaymentId(Integer paymentId);
}
