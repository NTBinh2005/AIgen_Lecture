package com.example.demo.repository;

import com.example.demo.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Page<Quiz> findByOwnerTeacher_UserIdAndTitleContainingIgnoreCase(Integer teacherId, String titleKeyword, Pageable pageable);
    Page<Quiz> findByOwnerTeacher_UserId(Integer teacherId, Pageable pageable);
}
