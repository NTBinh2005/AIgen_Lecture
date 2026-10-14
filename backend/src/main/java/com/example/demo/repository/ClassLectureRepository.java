package com.example.demo.repository;

import com.example.demo.entity.ClassLecture;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassLectureRepository extends JpaRepository<ClassLecture, Long> {
    boolean existsByClassEntity_ClassIdAndLecture_LectureId(Integer classId, Long lectureId);
    List<ClassLecture> findByClassEntity_ClassId(Integer classId);
    Optional<ClassLecture> findByClassEntity_ClassIdAndLecture_LectureId(Integer classId, Long lectureId);

    /**
     * FIX #1: Học sinh được xem bài giảng nếu bài đó được giao cho một lớp mà học sinh
     * đang có enrollment ACTIVE. Join ClassLecture với ClassStudent qua cùng class_id.
     */
    @Query("SELECT CASE WHEN COUNT(cl) > 0 THEN true ELSE false END "
            + "FROM ClassLecture cl, ClassStudent cs "
            + "WHERE cl.lecture.lectureId = :lectureId "
            + "AND cs.classEntity.classId = cl.classEntity.classId "
            + "AND cs.student.userId = :studentId "
            + "AND cs.status = com.example.demo.entity.EnrollmentStatus.ACTIVE")
    boolean existsActiveEnrollmentGrant(
            @Param("studentId") Integer studentId, @Param("lectureId") Long lectureId);
}
