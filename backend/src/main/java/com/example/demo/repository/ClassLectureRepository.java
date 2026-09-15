package com.example.demo.repository;

import com.example.demo.entity.ClassLecture;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassLectureRepository extends JpaRepository<ClassLecture, Long> {
    boolean existsByClassEntity_ClassIdAndLecture_LectureId(Integer classId, Long lectureId);
    List<ClassLecture> findByClassEntity_ClassId(Integer classId);
}
