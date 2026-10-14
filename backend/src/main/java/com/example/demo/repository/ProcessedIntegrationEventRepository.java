package com.example.demo.repository;

import com.example.demo.entity.ProcessedIntegrationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedIntegrationEventRepository
        extends JpaRepository<ProcessedIntegrationEvent, String> {}
