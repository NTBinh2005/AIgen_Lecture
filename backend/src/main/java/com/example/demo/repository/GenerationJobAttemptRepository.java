package com.example.demo.repository;

import com.example.demo.entity.GenerationJobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenerationJobAttemptRepository extends JpaRepository<GenerationJobAttempt, UUID> {

    List<GenerationJobAttempt> findByJob_JobIdOrderByAttemptNumberAsc(UUID jobId);

    Optional<GenerationJobAttempt> findByJob_JobIdAndAttemptNumber(UUID jobId, int attemptNumber);
}
