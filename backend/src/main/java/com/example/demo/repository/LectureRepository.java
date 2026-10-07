package com.example.demo.repository;

import com.example.demo.entity.Lecture;
import com.example.demo.entity.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho Lecture entity.
 * Soft-delete được xử lý tự động qua @Where(clause = "deleted_at IS NULL") trong entity.
 */
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    Optional<Lecture> findByBusinessId(UUID businessId);

    /**
     * Tìm tất cả bài giảng của một teacher (phân trang + sắp xếp).
     * Hỗ trợ Search/Sort/Paging/Filtering theo chuẩn REST API (FR task 23).
     */
    @EntityGraph(attributePaths = {"teacher"})
    Page<Lecture> findByTeacher_UserId(Integer teacherId, Pageable pageable);

    /**
     * Tìm kiếm bài giảng theo title (case-insensitive, hỗ trợ Filtering).
     */
    @EntityGraph(attributePaths = {"teacher"})
    Page<Lecture> findByTeacher_UserIdAndTitleContainingIgnoreCase(
            Integer teacherId, String titleKeyword, Pageable pageable);

    /**
     * Tìm kiếm bài giảng theo title trên toàn hệ thống (cho Student).
     */
    @EntityGraph(attributePaths = {"teacher"})
    Page<Lecture> findByTitleContainingIgnoreCase(String titleKeyword, Pageable pageable);

    /**
     * Lấy tất cả bài giảng có phân trang.
     */
    @EntityGraph(attributePaths = {"teacher"})
    Page<Lecture> findAll(Pageable pageable);

    @Query(value = """
            SELECT l FROM Lecture l
            WHERE l.teacher.userId = :userId
               OR l.lectureId IN (SELECT c.lecture.lectureId FROM LectureCollaborator c WHERE c.collaboratorUserId = :userId)
            """,
            countQuery = """
            SELECT COUNT(l) FROM Lecture l
            WHERE l.teacher.userId = :userId
               OR l.lectureId IN (SELECT c.lecture.lectureId FROM LectureCollaborator c WHERE c.collaboratorUserId = :userId)
            """)
    Page<Lecture> findOwnedOrShared(@Param("userId") Integer userId, Pageable pageable);

    @Query(value = """
            SELECT l FROM Lecture l
            WHERE (l.teacher.userId = :userId
               OR l.lectureId IN (SELECT c.lecture.lectureId FROM LectureCollaborator c WHERE c.collaboratorUserId = :userId))
              AND LOWER(l.title) LIKE LOWER(CONCAT('%', :title, '%'))
            """,
            countQuery = """
            SELECT COUNT(l) FROM Lecture l
            WHERE (l.teacher.userId = :userId
               OR l.lectureId IN (SELECT c.lecture.lectureId FROM LectureCollaborator c WHERE c.collaboratorUserId = :userId))
              AND LOWER(l.title) LIKE LOWER(CONCAT('%', :title, '%'))
            """)
    Page<Lecture> findOwnedOrSharedByTitle(
            @Param("userId") Integer userId,
            @Param("title") String title,
            Pageable pageable);

    /**
     * Lấy danh sách bài giảng đang trong trạng thái PROCESSING hoặc PENDING
     * để background job poll video-service.
     */
    @Query("SELECT l FROM Lecture l WHERE l.videoStatus IN :statuses AND l.videoJobId IS NOT NULL")
    List<Lecture> findByVideoStatusIn(@Param("statuses") List<VideoStatus> statuses);

    /** Lectures saved successfully but not submitted to the video service yet. */
    List<Lecture> findTop10ByVideoStatusAndVideoJobIdIsNullOrderByCreatedAtAsc(VideoStatus status);

    /**
     * FIX #1: Bài giảng học sinh được xem — đã PUBLISHED, phạm vi CLASS, và được giao
     * cho một lớp mà học sinh đang có enrollment ACTIVE. Hỗ trợ lọc theo title.
     */
    @Query(value = """
            SELECT DISTINCT l FROM Lecture l, ClassLecture cl, ClassStudent cs
            WHERE cl.lecture = l
              AND cs.classEntity = cl.classEntity
              AND cs.student.userId = :studentId
              AND cs.status = com.example.demo.entity.EnrollmentStatus.ACTIVE
              AND l.status = com.example.demo.entity.LectureStatus.PUBLISHED
              AND l.accessScope = com.example.demo.entity.LectureAccessScope.CLASS
              AND (:title IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :title, '%')))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT l) FROM Lecture l, ClassLecture cl, ClassStudent cs
            WHERE cl.lecture = l
              AND cs.classEntity = cl.classEntity
              AND cs.student.userId = :studentId
              AND cs.status = com.example.demo.entity.EnrollmentStatus.ACTIVE
              AND l.status = com.example.demo.entity.LectureStatus.PUBLISHED
              AND l.accessScope = com.example.demo.entity.LectureAccessScope.CLASS
              AND (:title IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :title, '%')))
            """)
    Page<Lecture> findPublishedForStudent(
            @Param("studentId") Integer studentId,
            @Param("title") String title,
            Pageable pageable);
}
