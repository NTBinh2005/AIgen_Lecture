package com.example.demo.repository;

import com.example.demo.entity.ClassLectureProgress;
import com.example.demo.entity.ClassLectureProgressId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassLectureProgressRepository extends JpaRepository<ClassLectureProgress, ClassLectureProgressId> {
    boolean existsByIdClassId(Integer classId);
    List<ClassLectureProgress> findByIdStudentIdAndIdClassId(Integer studentId, Integer classId);
    Optional<ClassLectureProgress> findByIdStudentIdAndIdClassIdAndIdLectureId(Integer studentId, Integer classId, Long lectureId);
}
