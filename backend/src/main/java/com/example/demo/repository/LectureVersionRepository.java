package com.example.demo.repository;

import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureVersionRepository extends JpaRepository<LectureVersion, UUID> {
    @org.springframework.data.jpa.repository.Query("select count(distinct v.lecture.lectureId) from LectureVersion v where v.aiGenerated = true and v.lecture.deletedAt is null and v.status <> com.example.demo.entity.LectureVersionStatus.DRAFT")
    long countAiGeneratedLectures();
    @org.springframework.data.jpa.repository.Query("select year(v.lecture.createdAt), month(v.lecture.createdAt), count(distinct v.lecture.lectureId) from LectureVersion v where v.aiGenerated = true and v.status <> com.example.demo.entity.LectureVersionStatus.DRAFT and v.lecture.deletedAt is null and v.lecture.createdAt >= :since group by year(v.lecture.createdAt), month(v.lecture.createdAt)")
    List<Object[]> monthlyAiGrowth(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    List<LectureVersion> findByLecture_LectureIdOrderByVersionNumberDesc(Long lectureId);

    Optional<LectureVersion> findByLecture_LectureIdAndVersionNumber(Long lectureId, int versionNumber);

    Optional<LectureVersion> findFirstByLecture_LectureIdAndStatusOrderByVersionNumberDesc(
            Long lectureId, LectureVersionStatus status);
}
