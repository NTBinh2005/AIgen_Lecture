package com.example.demo.repository;

import com.example.demo.entity.PresentationExport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresentationExportRepository extends JpaRepository<PresentationExport, UUID> {
    Optional<PresentationExport> findByPresentationVersion_PresentationVersionId(UUID versionId);
}
