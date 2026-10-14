package com.example.demo.repository;

import com.example.demo.entity.SessionResource;
import com.example.demo.entity.SessionResourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionResourceRepository extends JpaRepository<SessionResource, Long> {
    List<SessionResource> findBySession_SessionIdOrderByCreatedAtAsc(Long sessionId);
    Optional<SessionResource> findBySession_SessionIdAndResourceTypeAndResourceId(
            Long sessionId, SessionResourceType resourceType, Long resourceId);
    Optional<SessionResource> findByIdAndSession_SessionId(Long id, Long sessionId);
}
