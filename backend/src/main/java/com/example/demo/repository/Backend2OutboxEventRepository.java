package com.example.demo.repository;

import com.example.demo.entity.Backend2OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Backend2OutboxEventRepository extends JpaRepository<Backend2OutboxEvent, String> {
    List<Backend2OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();
}
