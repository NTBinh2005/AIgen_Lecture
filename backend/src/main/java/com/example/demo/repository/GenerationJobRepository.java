package com.example.demo.repository;

import com.example.demo.entity.GenerationJob;
import com.example.demo.entity.JobType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    Optional<GenerationJob> findByOwnerIdAndJobTypeAndIdempotencyKey(
            Integer ownerId,
            JobType jobType,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job where job.jobId = :jobId")
    Optional<GenerationJob> findByIdForUpdate(@Param("jobId") UUID jobId);
}
