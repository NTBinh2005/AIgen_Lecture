package com.example.demo.service;

import com.example.demo.entity.Backend2OutboxEvent;
import com.example.demo.repository.Backend2OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Backend2EventService {
    private final Backend2OutboxEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public void emit(String eventType, String aggregateType, Object aggregateId, Object payload) {
        Backend2OutboxEvent event = new Backend2OutboxEvent();
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(String.valueOf(aggregateId));
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize outbox event", ex);
        }
        eventRepository.save(event);
    }
}
