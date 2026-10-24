package com.example.demo.repository;

import com.example.demo.entity.ClassOrder;
import com.example.demo.entity.ClassOrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassOrderRepository extends JpaRepository<ClassOrder, Integer> {
    boolean existsByClassEntity_ClassId(Integer classId);
    Optional<ClassOrder> findByOrderCode(String orderCode);
    
    Optional<ClassOrder> findByStudentUserIdAndClassEntityClassIdAndStatus(Integer studentId, Integer classId, ClassOrderStatus status);

    boolean existsByStudentUserIdAndClassEntityClassIdAndStatus(Integer studentId, Integer classId, ClassOrderStatus status);

    boolean existsByStudentUserIdAndClassEntityClassIdAndStatusIn(
            Integer studentId, Integer classId, List<ClassOrderStatus> statuses);

    Page<ClassOrder> findByStudentUserIdOrderByCreatedAtDesc(Integer studentId, Pageable pageable);

    Page<ClassOrder> findByClassEntityClassIdOrderByCreatedAtDesc(Integer classId, Pageable pageable);

    List<ClassOrder> findByStatusAndHoldUntilLessThanEqualAndSettledAtIsNull(ClassOrderStatus status, Instant holdUntil);
}
