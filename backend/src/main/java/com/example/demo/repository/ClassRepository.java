package com.example.demo.repository;

import com.example.demo.entity.ClassEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {

    // ── Unique check theo (classCode, semester) — CLASS-BR exception ─────────
    boolean existsByClassCodeAndSemester(String classCode, String semester);

    boolean existsByClassCodeAndSemesterAndClassIdNot(String classCode, String semester, Integer classId);

    // ── Unique check theo (classCode) khi semester null ──────────────────────
    boolean existsByClassCodeAndSemesterIsNull(String classCode);

    boolean existsByClassCodeAndSemesterIsNullAndClassIdNot(String classCode, Integer classId);

    // ── Tìm lớp theo teacher chính — ENRL-01 ─────────────────────────────────
    List<ClassEntity> findByTeacher_UserId(Integer teacherId);

    // ── Tìm lớp theo classCode — ENRL-06 self-enroll ─────────────────────────
    Optional<ClassEntity> findByClassCode(String classCode);

    @Query("select distinct c from ClassEntity c left join ClassTeacher ct on ct.classEntity = c " +
            "where c.teacher.userId = :teacherId or ct.teacher.userId = :teacherId")
    List<ClassEntity> findManagedByTeacher(@Param("teacherId") Integer teacherId);
}
