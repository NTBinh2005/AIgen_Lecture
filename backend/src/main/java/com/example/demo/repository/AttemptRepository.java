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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.attemptId = :id")
    Optional<Attempt> lockByAttemptId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.assignment.assignmentId = :id order by a.attemptId")
    List<Attempt> lockByAssignmentId(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Attempt> findByAssignment_AssignmentIdAndStudent_UserIdAndAttemptNo(Long assignmentId, Integer studentId, Integer attemptNo);

    List<Attempt> findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(Long assignmentId, Integer studentId);

    List<Attempt> findByStatusAndDeadlineAtBefore(AttemptStatus status, LocalDateTime now);

    List<Attempt> findByAssignment_AssignmentId(Long assignmentId);
    org.springframework.data.domain.Page<Attempt> findByAssignment_AssignmentId(
            Long assignmentId, org.springframework.data.domain.Pageable pageable);

    /** Bug 5 fix: returns all completed (non-IN_PROGRESS) attempts for a student, newest first. */
    List<Attempt> findByStudent_UserIdAndStatusNotOrderBySubmittedAtDesc(Integer studentId, AttemptStatus excludedStatus);
    org.springframework.data.domain.Page<Attempt> findByStudent_UserIdAndStatusNot(
            Integer studentId, AttemptStatus excludedStatus,
            org.springframework.data.domain.Pageable pageable);
}
