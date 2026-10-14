package com.example.demo.repository;

import com.example.demo.entity.PresentationVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresentationVersionRepository extends JpaRepository<PresentationVersion, UUID> {
    List<PresentationVersion> findByPresentation_PresentationIdOrderByVersionNumberDesc(UUID presentationId);

    Optional<PresentationVersion> findByPresentation_PresentationIdAndVersionNumber(
            UUID presentationId, int versionNumber);
}
