package com.example.demo.repository;

import com.example.demo.entity.ClassEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassEntity c where c.classId = :id")
    Optional<ClassEntity> lockByClassId(@Param("id") Integer id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassEntity c where c.classCode = :code")
    Optional<ClassEntity> lockByClassCode(@Param("code") String code);

    // ── Unique check theo (classCode, semester) — CLASS-BR exception ─────────
    boolean existsByClassCodeAndSemester(String classCode, String semester);

    boolean existsByClassCodeAndSemesterAndClassIdNot(String classCode, String semester, Integer classId);

    // ── Unique check theo (classCode) khi semester null ──────────────────────
    boolean existsByClassCodeAndSemesterIsNull(String classCode);

    boolean existsByClassCodeAndSemesterIsNullAndClassIdNot(String classCode, Integer classId);
    boolean existsByClassCodeIgnoreCase(String classCode);
    boolean existsByClassCodeIgnoreCaseAndClassIdNot(String classCode, Integer classId);

    boolean existsByTeacher_UserIdAndClassNameIgnoreCase(Integer teacherId, String className);

    boolean existsByTeacher_UserIdAndClassNameIgnoreCaseAndClassIdNot(
            Integer teacherId, String className, Integer classId);

    // ── Tìm lớp theo teacher chính — ENRL-01 ─────────────────────────────────
    List<ClassEntity> findByTeacher_UserId(Integer teacherId);

    // ── Tìm lớp theo classCode — ENRL-06 self-enroll ─────────────────────────
    Optional<ClassEntity> findByClassCode(String classCode);

    @Query("select distinct c from ClassEntity c left join ClassTeacher ct on ct.classEntity = c " +
            "where c.teacher.userId = :teacherId or ct.teacher.userId = :teacherId")
    List<ClassEntity> findManagedByTeacher(@Param("teacherId") Integer teacherId);

    @Query("select distinct c from ClassEntity c left join ClassTeacher ct on ct.classEntity = c " +
            "where c.teacher.userId = :teacherId or ct.teacher.userId = :teacherId")
    org.springframework.data.domain.Page<ClassEntity> findManagedByTeacher(
            @Param("teacherId") Integer teacherId, org.springframework.data.domain.Pageable pageable);

    @Query("select cs.classEntity from ClassStudent cs where cs.student.userId = :studentId " +
            "and cs.status in (com.example.demo.entity.EnrollmentStatus.ACTIVE, " +
            "com.example.demo.entity.EnrollmentStatus.COMPLETED)")
    org.springframework.data.domain.Page<ClassEntity> findVisibleToStudent(
            @Param("studentId") Integer studentId, org.springframework.data.domain.Pageable pageable);
}
