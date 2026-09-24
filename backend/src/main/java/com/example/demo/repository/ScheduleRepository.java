package com.example.demo.repository;

import com.example.demo.entity.Schedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByClassEntity_ClassId(Integer classId);
    List<Schedule> findByClassEntity_ClassIdInOrderByStartsAtAsc(List<Integer> classIds);
    List<Schedule> findAllByOrderByStartsAtAsc();
}
