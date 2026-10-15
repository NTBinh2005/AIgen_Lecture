package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationStreamServiceTest {
    private final NotificationStreamService service = new NotificationStreamService();

    @AfterEach
    void closeStreams() {
        service.closeUser(42);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sixthConnectionEvictsOldestInsteadOfRejectingNewStream() {
        SseEmitter oldest = service.subscribe(42);
        for (int i = 0; i < 4; i++) {
            service.subscribe(42);
        }

        assertThatCode(() -> service.subscribe(42)).doesNotThrowAnyException();

        ConcurrentHashMap<Integer, Deque<SseEmitter>> streams =
                (ConcurrentHashMap<Integer, Deque<SseEmitter>>) ReflectionTestUtils.getField(service, "streams");
        assertThat(streams).isNotNull();
        assertThat(streams.get(42)).hasSize(5).doesNotContain(oldest);
    }

    @Test
    void failedOrCompletedStreamsCanBeRemovedSafely() {
        service.subscribe(42);
        assertThatCode(() -> service.afterCommit(42, Map.of("message", "test")))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.closeUser(42)).doesNotThrowAnyException();
    }
}
