package com.example.demo.service;

import com.example.demo.dto.request.GenerationJobCompletionRequest;
import com.example.demo.dto.request.GenerationJobCreateRequest;
import com.example.demo.dto.request.GenerationJobFailureRequest;
import com.example.demo.dto.request.GenerationJobProgressRequest;
import com.example.demo.dto.response.GenerationJobResponse;

import java.util.UUID;

/**
 * Generation job contract.
 *
 * The actor-aware methods back the HTTP API. The methods carrying an
 * {@code attemptId} form the trusted internal API for Lecture/Presentation
 * workers. The attempt token fences callbacks from an older retry attempt.
 */
public interface GenerationJobService {

    GenerationJobResponse createOrGet(GenerationJobCreateRequest request);

    GenerationJobResponse get(UUID jobId, Integer requesterId, boolean requesterIsAdmin);

    GenerationJobResponse retry(UUID jobId, Integer requesterId, boolean requesterIsAdmin);

    GenerationJobResponse cancel(UUID jobId, Integer requesterId, boolean requesterIsAdmin);

    GenerationJobResponse start(UUID jobId, UUID attemptId, String currentStep);

    GenerationJobResponse updateProgress(
            UUID jobId,
            UUID attemptId,
            GenerationJobProgressRequest request
    );

    GenerationJobResponse complete(
            UUID jobId,
            UUID attemptId,
            GenerationJobCompletionRequest request
    );

    GenerationJobResponse fail(
            UUID jobId,
            UUID attemptId,
            GenerationJobFailureRequest request
    );
}
