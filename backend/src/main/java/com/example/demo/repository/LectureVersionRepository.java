package com.example.demo.repository;

import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureVersionRepository extends JpaRepository<LectureVersion, UUID> {
    List<LectureVersion> findByLecture_LectureIdOrderByVersionNumberDesc(Long lectureId);

    Optional<LectureVersion> findByLecture_LectureIdAndVersionNumber(Long lectureId, int versionNumber);

    Optional<LectureVersion> findFirstByLecture_LectureIdAndStatusOrderByVersionNumberDesc(
            Long lectureId, LectureVersionStatus status);
}
