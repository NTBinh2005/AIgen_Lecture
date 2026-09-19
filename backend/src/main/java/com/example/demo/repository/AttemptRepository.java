package com.example.demo.repository;

import com.example.demo.entity.Attempt;
import com.example.demo.entity.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByAssignment_AssignmentIdAndStudent_UserIdAndAttemptNo(Long assignmentId, Integer studentId, Integer attemptNo);
    
    List<Attempt> findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(Long assignmentId, Integer studentId);
    
    List<Attempt> findByStatusAndDeadlineAtBefore(AttemptStatus status, LocalDateTime now);
    
    List<Attempt> findByAssignment_AssignmentId(Long assignmentId);
}
