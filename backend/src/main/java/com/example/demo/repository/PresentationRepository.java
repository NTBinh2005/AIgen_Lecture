package com.example.demo.repository;

import com.example.demo.entity.Presentation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresentationRepository extends JpaRepository<Presentation, UUID> {
    @Query(value = """
            SELECT DISTINCT p FROM Presentation p
            LEFT JOIN PresentationCollaborator c ON c.presentation = p
            WHERE p.archivedAt IS NULL
              AND (p.ownerId = :userId OR c.collaboratorUserId = :userId)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p) FROM Presentation p
            LEFT JOIN PresentationCollaborator c ON c.presentation = p
            WHERE p.archivedAt IS NULL
              AND (p.ownerId = :userId OR c.collaboratorUserId = :userId)
            """)
    Page<Presentation> findVisibleTo(
            @Param("userId") Integer userId,
            Pageable pageable);

    @Query(value = """
            SELECT DISTINCT p FROM Presentation p
            LEFT JOIN PresentationCollaborator c ON c.presentation = p
            WHERE p.archivedAt IS NULL
              AND (p.ownerId = :userId OR c.collaboratorUserId = :userId)
              AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p) FROM Presentation p
            LEFT JOIN PresentationCollaborator c ON c.presentation = p
            WHERE p.archivedAt IS NULL
              AND (p.ownerId = :userId OR c.collaboratorUserId = :userId)
              AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))
            """)
    Page<Presentation> findVisibleToByTitle(
            @Param("userId") Integer userId,
            @Param("title") String title,
            Pageable pageable);
}
