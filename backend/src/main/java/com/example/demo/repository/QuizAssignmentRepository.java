package com.example.demo.repository;

import com.example.demo.entity.AssignmentStatus;
import com.example.demo.entity.QuizAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAssignmentRepository extends JpaRepository<QuizAssignment, Long> {
    boolean existsByClassEntity_ClassId(Integer classId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from QuizAssignment a where a.assignmentId = :id")
    java.util.Optional<QuizAssignment> lockByAssignmentId(@org.springframework.data.repository.query.Param("id") Long id);
    List<QuizAssignment> findByStatusAndCloseAtBetween(AssignmentStatus status, java.time.LocalDateTime from, java.time.LocalDateTime to);
    List<QuizAssignment> findByClassEntity_ClassIdAndStatus(Integer classId, AssignmentStatus status);
    List<QuizAssignment> findByClassEntity_ClassId(Integer classId);
}
