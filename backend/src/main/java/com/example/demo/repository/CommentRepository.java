package com.example.demo.repository;

import com.example.demo.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Lấy toàn bộ comment chưa bị xóa của một bài giảng,
     * sắp xếp theo thời gian tạo tăng dần (cũ nhất trên đầu).
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.lecture.lectureId = :lectureId
              AND c.isDeleted = false
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findByLectureId(@Param("lectureId") Long lectureId);
}
