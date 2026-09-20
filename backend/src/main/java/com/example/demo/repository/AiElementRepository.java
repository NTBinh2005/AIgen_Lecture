package com.example.demo.repository;

import com.example.demo.entity.AiElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiElementRepository extends JpaRepository<AiElement, Long> {

    /** Lấy tất cả câu hỏi của một lecture, sắp xếp theo thứ tự hiển thị. */
    List<AiElement> findByLecture_LectureIdOrderByOrderIndexAsc(Long lectureId);
}
