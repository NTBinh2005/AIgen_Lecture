package com.example.demo.service.serviceImpl;

import com.example.demo.service.event.LectureVideoRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LectureVideoRenderProcessor {
    private final VideoRenderService videoRenderService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void process(LectureVideoRequestedEvent event) {
        try {
            videoRenderService.requestRender(event.lectureId(), event.slideContent());
        } catch (RuntimeException exception) {
            log.error("Could not request video render for lecture {}", event.lectureId(), exception);
        }
    }
}
