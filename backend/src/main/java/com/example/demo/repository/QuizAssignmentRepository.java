package com.example.demo.repository;

import com.example.demo.entity.AssignmentStatus;
import com.example.demo.entity.QuizAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAssignmentRepository extends JpaRepository<QuizAssignment, Long> {
    List<QuizAssignment> findByClassEntity_ClassIdAndStatus(Integer classId, AssignmentStatus status);
    List<QuizAssignment> findByClassEntity_ClassId(Integer classId);
}
