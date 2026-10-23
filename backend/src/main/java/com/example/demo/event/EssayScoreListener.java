package com.example.demo.event;

import com.example.demo.entity.AttemptStatus;
import com.example.demo.repository.AttemptAnswerRepository;
import com.example.demo.repository.AttemptRepository;
import com.example.demo.service.LlmService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class EssayScoreListener {
    private final LlmService llm;
    private final AttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final PlatformTransactionManager transactions;

    @Async("taskExecutor")
    @TransactionalEventListener
    public void suggest(EssayScoreRequestedEvent event) {
        try {
            if (!Double.isFinite(event.maxPoints()) || event.maxPoints() <= 0) return;
            Double score = llm.aiSuggestScore(event.question(), event.response(),
                    "Điểm tối đa: " + event.maxPoints() + ". " + (event.explanation() == null ? "" : event.explanation()));
            if (score == null || !Double.isFinite(score)) return;
            double bounded = Math.max(0, Math.min(event.maxPoints(), score));
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var attempt = attempts.lockByAttemptId(event.attemptId()).orElse(null);
                if (attempt == null || attempt.getStatus() == AttemptStatus.IN_PROGRESS) return;
                answers.findById(event.answerId()).filter(answer ->
                        answer.getAttempt().getAttemptId().equals(event.attemptId())
                        && Objects.equals(answer.getAnswerVersion(), event.answerVersion())
                        && Objects.equals(answer.getResponse(), event.response())
                        && answer.getTeacherFinalScore() == null)
                        .ifPresent(answer -> answer.setAiSuggestedScore(bounded));
            });
        } catch (Exception ex) {
            log.warn("Could not suggest essay score for answer {}", event.answerId(), ex);
        }
    }
}
