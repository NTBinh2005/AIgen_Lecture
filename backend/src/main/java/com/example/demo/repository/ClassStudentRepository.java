package com.example.demo.repository;

import com.example.demo.entity.ClassStudent;
import com.example.demo.entity.ClassStudentId;
import com.example.demo.entity.EnrollmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, ClassStudentId> {
    List<ClassStudent> findByClassEntity_ClassId(Integer classId);

    List<ClassStudent> findByStudent_UserId(Integer studentId);

    // ENRL-02: student chỉ thấy lớp có enrollment ACTIVE hoặc COMPLETED
    List<ClassStudent> findByStudent_UserIdAndStatusIn(Integer studentId, List<EnrollmentStatus> statuses);

    // Kiểm tra student đã ghi danh (và đang active) chưa — security check
    Optional<ClassStudent> findByClassEntity_ClassIdAndStudent_UserIdAndStatusIn(
            Integer classId, Integer studentId, List<EnrollmentStatus> statuses);

    long countByClassEntity_ClassIdAndStatusIn(Integer classId, List<EnrollmentStatus> statuses);
}
