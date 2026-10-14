package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.GenerationJobCompletionRequest;
import com.example.demo.dto.request.GenerationJobFailureRequest;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.repository.PresentationRepository;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.PresentationService;
import com.example.demo.service.event.PresentationGenerationQueuedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresentationGenerationResultService {
    private final PresentationRepository presentationRepository;
    private final PresentationService presentationService;
    private final GenerationJobService generationJobService;

    @Transactional
    public void complete(
            PresentationGenerationQueuedEvent event,
            List<PresentationSlideRequest> slides) {
        String title = presentationRepository.findById(event.presentationId())
                .orElseThrow(() -> new IllegalStateException("Presentation was removed during generation"))
                .getTitle();
        PresentationResponse response = presentationService.completeGeneration(
                event.presentationId(), event.ownerId(), title, slides);
        generationJobService.complete(
                event.jobId(),
                event.attemptId(),
                new GenerationJobCompletionRequest(
                        "presentation-version:" + response.currentVersionId(),
                        "PRESENTATION",
                        event.presentationId().toString()));
    }

    @Transactional
    public void fail(PresentationGenerationQueuedEvent event) {
        try {
            generationJobService.fail(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobFailureRequest(
                            "PRESENTATION_GENERATION_FAILED",
                            "The presentation could not be generated. Please retry the job."));
        } finally {
            presentationService.failGeneration(event.presentationId());
        }
    }
}
