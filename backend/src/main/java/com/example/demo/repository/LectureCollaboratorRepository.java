package com.example.demo.repository;

import com.example.demo.entity.LectureCollaborator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureCollaboratorRepository extends JpaRepository<LectureCollaborator, UUID> {
    boolean existsByLecture_LectureIdAndCollaboratorUserId(Long lectureId, Integer collaboratorUserId);

    List<LectureCollaborator> findByLecture_LectureIdOrderByCreatedAtAsc(Long lectureId);

    void deleteByLecture_LectureIdAndCollaboratorUserId(Long lectureId, Integer collaboratorUserId);
}
