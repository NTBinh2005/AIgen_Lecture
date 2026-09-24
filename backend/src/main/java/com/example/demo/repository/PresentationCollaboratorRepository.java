package com.example.demo.repository;

import com.example.demo.entity.PresentationCollaborator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresentationCollaboratorRepository
        extends JpaRepository<PresentationCollaborator, UUID> {
    boolean existsByPresentation_PresentationIdAndCollaboratorUserId(
            UUID presentationId, Integer collaboratorUserId);

    List<PresentationCollaborator> findByPresentation_PresentationIdOrderByCreatedAtAsc(
            UUID presentationId);

    void deleteByPresentation_PresentationIdAndCollaboratorUserId(
            UUID presentationId, Integer collaboratorUserId);
}
