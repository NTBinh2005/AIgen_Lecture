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

/**
 * Repository cho Lecture entity.
 * Soft-delete được xử lý tự động qua @Where(clause = "deleted_at IS NULL") trong entity.
 */
public interface LectureRepository extends JpaRepository<Lecture, Long> {

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

    /**
     * Lấy danh sách bài giảng đang trong trạng thái PROCESSING hoặc PENDING
     * để background job poll video-service.
     */
    @Query("SELECT l FROM Lecture l WHERE l.videoStatus IN :statuses AND l.videoJobId IS NOT NULL")
    List<Lecture> findByVideoStatusIn(@Param("statuses") List<VideoStatus> statuses);
}
