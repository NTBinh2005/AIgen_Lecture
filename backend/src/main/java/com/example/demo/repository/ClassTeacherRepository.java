package com.example.demo.repository;

import com.example.demo.entity.ClassTeacher;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    boolean existsByClassEntity_ClassIdAndTeacher_UserId(Integer classId, Integer teacherId);

    List<ClassTeacher> findByClassEntity_ClassId(Integer classId);

    void deleteByClassEntity_ClassId(Integer classId);

    // Xóa các co-teacher không còn trong danh sách mới
    void deleteByClassEntity_ClassIdAndTeacher_UserIdIn(Integer classId, List<Integer> teacherIds);
}
