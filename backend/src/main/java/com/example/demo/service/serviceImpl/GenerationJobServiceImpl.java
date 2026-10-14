package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.GenerationJobCompletionRequest;
import com.example.demo.dto.request.GenerationJobCreateRequest;
import com.example.demo.dto.request.GenerationJobFailureRequest;
import com.example.demo.dto.request.GenerationJobProgressRequest;
import com.example.demo.dto.response.GenerationJobAttemptResponse;
import com.example.demo.dto.response.GenerationJobResponse;
import com.example.demo.entity.GenerationJob;
import com.example.demo.entity.GenerationJobAttempt;
import com.example.demo.entity.JobStatus;
import com.example.demo.entity.JobType;
import com.example.demo.repository.GenerationJobAttemptRepository;
import com.example.demo.repository.GenerationJobRepository;
import com.example.demo.service.GenerationJobService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GenerationJobServiceImpl implements GenerationJobService {

    private static final String QUEUED_STEP = "QUEUED";
    private static final String PROCESSING_STEP = "PROCESSING";
    private static final String DONE_STEP = "DONE";
    private static final String FAILED_STEP = "FAILED";
    private static final String CANCELLED_STEP = "CANCELLED";

    private final GenerationJobRepository jobRepository;
    private final GenerationJobAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int configuredMaxAttempts;

    public GenerationJobServiceImpl(
            GenerationJobRepository jobRepository,
            GenerationJobAttemptRepository attemptRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.generation-job.max-attempts:3}") int configuredMaxAttempts
    ) {
        if (configuredMaxAttempts < 1) {
            throw new IllegalArgumentException("app.generation-job.max-attempts must be at least 1");
        }
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.configuredMaxAttempts = configuredMaxAttempts;
    }

    /**
     * Idempotent creation is protected twice: an application lookup handles
     * normal retries and the database unique constraint handles concurrent
     * requests. TransactionTemplate lets a constraint race fully roll back
     * before the winning row is read.
     */
    @Override
    public GenerationJobResponse createOrGet(GenerationJobCreateRequest request) {
        NormalizedCreate normalized = normalizeCreate(request);

        GenerationJob existing = findByIdempotency(normalized);
        if (existing != null) {
            assertSameIdempotentPayload(existing, normalized);
            return responseWithHistory(existing);
        }

        try {
            GenerationJobResponse created = transactionTemplate.execute(status -> {
                GenerationJob foundInsideTransaction = findByIdempotency(normalized);
                if (foundInsideTransaction != null) {
                    assertSameIdempotentPayload(foundInsideTransaction, normalized);
                    return responseWithHistory(foundInsideTransaction);
                }

                GenerationJob job = new GenerationJob();
                job.setJobId(UUID.randomUUID());
                job.setJobType(normalized.jobType());
                job.setStatus(JobStatus.QUEUED);
                job.setOwnerId(normalized.ownerId());
                job.setIdempotencyKey(normalized.idempotencyKey());
                job.setSourceType(normalized.sourceType());
                job.setSourceId(normalized.sourceId());
                job.setSourceAssetId(normalized.sourceAssetId());
                job.setTemplateId(normalized.templateId());
                job.setParametersJson(normalized.parametersJson());
                job.setTargetType(normalized.targetType());
                job.setTargetId(normalized.targetId());
                job.setProgress(0);
                job.setCurrentStep(QUEUED_STEP);
                job.setAttemptCount(1);
                job.setMaxAttempts(configuredMaxAttempts);
                jobRepository.save(job);

                GenerationJobAttempt attempt = newAttempt(job, 1);
                attemptRepository.save(attempt);
                return toResponse(job, List.of(attempt));
            });
            if (created == null) {
                throw new IllegalStateException("Generation job transaction returned no result");
            }
            return created;
        } catch (DataIntegrityViolationException race) {
            GenerationJob winner = findByIdempotency(normalized);
            if (winner == null) {
                throw race;
            }
            assertSameIdempotentPayload(winner, normalized);
            return responseWithHistory(winner);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationJobResponse get(UUID jobId, Integer requesterId, boolean requesterIsAdmin) {
        GenerationJob job = findJob(jobId);
        assertOwnerOrAdmin(job, requesterId, requesterIsAdmin);
        return responseWithHistory(job);
    }

    @Override
    @Transactional
    public GenerationJobResponse retry(UUID jobId, Integer requesterId, boolean requesterIsAdmin) {
        GenerationJob job = findJobForUpdate(jobId);
        assertOwnerOrAdmin(job, requesterId, requesterIsAdmin);
        requireStatus(job, JobStatus.FAILED, "Only FAILED jobs can be retried");

        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            throw new ConflictException("Generation job reached its maximum retry attempts");
        }

        int nextAttemptNumber = job.getAttemptCount() + 1;
        job.setAttemptCount(nextAttemptNumber);
        job.setStatus(JobStatus.QUEUED);
        job.setProgress(0);
        job.setCurrentStep(QUEUED_STEP);
        job.setSafeErrorCode(null);
        job.setSafeErrorMessage(null);
        job.setResultReference(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);

        GenerationJobAttempt nextAttempt = newAttempt(job, nextAttemptNumber);
        attemptRepository.save(nextAttempt);
        return responseWithHistory(job);
    }

    @Override
    @Transactional
    public GenerationJobResponse cancel(UUID jobId, Integer requesterId, boolean requesterIsAdmin) {
        GenerationJob job = findJobForUpdate(jobId);
        assertOwnerOrAdmin(job, requesterId, requesterIsAdmin);

        GenerationJobAttempt attempt = currentAttempt(job);
        if (job.getStatus() == JobStatus.CANCELLED) {
            return responseWithHistory(job);
        }
        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.PROCESSING) {
            throw new ConflictException("Only QUEUED or PROCESSING jobs can be cancelled");
        }

        Instant now = Instant.now();
        job.setStatus(JobStatus.CANCELLED);
        job.setCurrentStep(CANCELLED_STEP);
        job.setFinishedAt(now);
        attempt.setStatus(JobStatus.CANCELLED);
        attempt.setCurrentStep(CANCELLED_STEP);
        attempt.setFinishedAt(now);
        return responseWithHistory(job);
    }

    @Override
    @Transactional
    public GenerationJobResponse start(UUID jobId, UUID attemptId, String currentStep) {
        GenerationJob job = findJobForUpdate(jobId);
        GenerationJobAttempt attempt = assertCurrentAttempt(job, attemptId);

        if (job.getStatus() == JobStatus.PROCESSING) {
            return responseWithHistory(job);
        }
        requireStatus(job, JobStatus.QUEUED, "Only QUEUED jobs can start processing");

        Instant now = Instant.now();
        String step = normalizeOptional(currentStep, "currentStep", 120);
        if (step == null) {
            step = PROCESSING_STEP;
        }
        job.setStatus(JobStatus.PROCESSING);
        job.setCurrentStep(step);
        job.setStartedAt(now);
        job.setFinishedAt(null);
        attempt.setStatus(JobStatus.PROCESSING);
        attempt.setCurrentStep(step);
        attempt.setStartedAt(now);
        attempt.setFinishedAt(null);
        return responseWithHistory(job);
    }

    @Override
    @Transactional
    public GenerationJobResponse updateProgress(
            UUID jobId,
            UUID attemptId,
            GenerationJobProgressRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Progress request is required");
        }
        if (request.progress() < 0 || request.progress() > 100) {
            throw new BadRequestException("progress must be between 0 and 100");
        }
        String step = normalizeRequired(request.currentStep(), "currentStep", 120);

        GenerationJob job = findJobForUpdate(jobId);
        GenerationJobAttempt attempt = assertCurrentAttempt(job, attemptId);
        requireStatus(job, JobStatus.PROCESSING, "Only PROCESSING jobs can report progress");
        if (request.progress() < job.getProgress()) {
            throw new ConflictException("Progress cannot move backwards");
        }

        job.setProgress(request.progress());
        job.setCurrentStep(step);
        attempt.setProgress(request.progress());
        attempt.setCurrentStep(step);
        return responseWithHistory(job);
    }

    @Override
    @Transactional
    public GenerationJobResponse complete(
            UUID jobId,
            UUID attemptId,
            GenerationJobCompletionRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Completion request is required");
        }
        String resultReference = normalizeRequired(request.resultReference(), "resultReference", 1000);
        String targetType = normalizeOptional(request.targetType(), "targetType", 50);
        String targetId = normalizeOptional(request.targetId(), "targetId", 255);
        assertPair(targetType, targetId, "targetType", "targetId");

        GenerationJob job = findJobForUpdate(jobId);
        GenerationJobAttempt attempt = assertCurrentAttempt(job, attemptId);
        if (job.getStatus() == JobStatus.DONE
                && Objects.equals(job.getResultReference(), resultReference)
                && Objects.equals(job.getTargetType(), targetType == null ? job.getTargetType() : targetType)
                && Objects.equals(job.getTargetId(), targetId == null ? job.getTargetId() : targetId)) {
            return responseWithHistory(job);
        }
        requireStatus(job, JobStatus.PROCESSING, "Only PROCESSING jobs can complete");

        if (targetType != null) {
            job.setTargetType(targetType);
            job.setTargetId(targetId);
        }
        Instant now = Instant.now();
        job.setStatus(JobStatus.DONE);
        job.setProgress(100);
        job.setCurrentStep(DONE_STEP);
        job.setSafeErrorCode(null);
        job.setSafeErrorMessage(null);
        job.setResultReference(resultReference);
        job.setFinishedAt(now);
        attempt.setStatus(JobStatus.DONE);
        attempt.setProgress(100);
        attempt.setCurrentStep(DONE_STEP);
        attempt.setSafeErrorCode(null);
        attempt.setSafeErrorMessage(null);
        attempt.setResultReference(resultReference);
        attempt.setFinishedAt(now);
        return responseWithHistory(job);
    }

    @Override
    @Transactional
    public GenerationJobResponse fail(
            UUID jobId,
            UUID attemptId,
            GenerationJobFailureRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Failure request is required");
        }
        String errorCode = normalizeRequired(request.safeErrorCode(), "safeErrorCode", 80);
        String errorMessage = normalizeRequired(request.safeErrorMessage(), "safeErrorMessage", 1000);

        GenerationJob job = findJobForUpdate(jobId);
        GenerationJobAttempt attempt = assertCurrentAttempt(job, attemptId);
        if (job.getStatus() == JobStatus.FAILED
                && Objects.equals(job.getSafeErrorCode(), errorCode)
                && Objects.equals(job.getSafeErrorMessage(), errorMessage)) {
            return responseWithHistory(job);
        }
        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.PROCESSING) {
            throw new ConflictException("Only QUEUED or PROCESSING jobs can fail");
        }

        Instant now = Instant.now();
        job.setStatus(JobStatus.FAILED);
        job.setCurrentStep(FAILED_STEP);
        job.setSafeErrorCode(errorCode);
        job.setSafeErrorMessage(errorMessage);
        job.setResultReference(null);
        job.setFinishedAt(now);
        attempt.setStatus(JobStatus.FAILED);
        attempt.setCurrentStep(FAILED_STEP);
        attempt.setSafeErrorCode(errorCode);
        attempt.setSafeErrorMessage(errorMessage);
        attempt.setResultReference(null);
        attempt.setFinishedAt(now);
        return responseWithHistory(job);
    }

    private GenerationJob findJob(UUID jobId) {
        if (jobId == null) {
            throw new BadRequestException("jobId is required");
        }
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found: " + jobId));
    }

    private GenerationJob findJobForUpdate(UUID jobId) {
        if (jobId == null) {
            throw new BadRequestException("jobId is required");
        }
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found: " + jobId));
    }

    private GenerationJobAttempt currentAttempt(GenerationJob job) {
        return attemptRepository.findByJob_JobIdAndAttemptNumber(job.getJobId(), job.getAttemptCount())
                .orElseThrow(() -> new IllegalStateException(
                        "Current attempt is missing for generation job " + job.getJobId()));
    }

    private GenerationJobAttempt assertCurrentAttempt(GenerationJob job, UUID attemptId) {
        if (attemptId == null) {
            throw new BadRequestException("attemptId is required");
        }
        GenerationJobAttempt attempt = currentAttempt(job);
        if (!attempt.getAttemptId().equals(attemptId)) {
            throw new ConflictException("Stale generation job attempt");
        }
        return attempt;
    }

    private void assertOwnerOrAdmin(GenerationJob job, Integer requesterId, boolean requesterIsAdmin) {
        if (requesterIsAdmin) {
            return;
        }
        if (requesterId == null || !requesterId.equals(job.getOwnerId())) {
            throw new AccessDeniedException("You do not have access to this generation job");
        }
    }

    private void requireStatus(GenerationJob job, JobStatus expected, String message) {
        if (job.getStatus() != expected) {
            throw new ConflictException(message + "; current status is " + job.getStatus());
        }
    }

    private GenerationJobAttempt newAttempt(GenerationJob job, int attemptNumber) {
        GenerationJobAttempt attempt = new GenerationJobAttempt();
        attempt.setAttemptId(UUID.randomUUID());
        attempt.setJob(job);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(JobStatus.QUEUED);
        attempt.setProgress(0);
        attempt.setCurrentStep(QUEUED_STEP);
        return attempt;
    }

    private GenerationJob findByIdempotency(NormalizedCreate request) {
        return jobRepository.findByOwnerIdAndJobTypeAndIdempotencyKey(
                request.ownerId(), request.jobType(), request.idempotencyKey()
        ).orElse(null);
    }

    private void assertSameIdempotentPayload(GenerationJob existing, NormalizedCreate request) {
        boolean same = Objects.equals(existing.getSourceType(), request.sourceType())
                && Objects.equals(existing.getSourceId(), request.sourceId())
                && Objects.equals(existing.getSourceAssetId(), request.sourceAssetId())
                && Objects.equals(existing.getTemplateId(), request.templateId())
                && Objects.equals(existing.getTargetType(), request.targetType())
                && Objects.equals(existing.getTargetId(), request.targetId())
                && Objects.equals(parseJson(existing.getParametersJson()), parseJson(request.parametersJson()));
        if (!same) {
            throw new ConflictException("Idempotency key was already used with a different request");
        }
    }

    private NormalizedCreate normalizeCreate(GenerationJobCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("Generation job request is required");
        }
        if (request.jobType() == null) {
            throw new BadRequestException("jobType is required");
        }
        if (request.ownerId() == null || request.ownerId() <= 0) {
            throw new BadRequestException("ownerId must be positive");
        }
        String sourceType = normalizeRequired(request.sourceType(), "sourceType", 50);
        String sourceId = normalizeOptional(request.sourceId(), "sourceId", 255);
        if (sourceId == null && request.sourceAssetId() == null) {
            throw new BadRequestException("sourceId or sourceAssetId is required");
        }
        String targetType = normalizeOptional(request.targetType(), "targetType", 50);
        String targetId = normalizeOptional(request.targetId(), "targetId", 255);
        assertPair(targetType, targetId, "targetType", "targetId");
        String idempotencyKey = normalizeRequired(request.idempotencyKey(), "idempotencyKey", 128);

        JsonNode parameters = request.parameters() == null
                ? JsonNodeFactory.instance.objectNode()
                : request.parameters();
        if (!parameters.isObject()) {
            throw new BadRequestException("parameters must be a JSON object");
        }

        return new NormalizedCreate(
                request.jobType(),
                request.ownerId(),
                sourceType,
                sourceId,
                request.sourceAssetId(),
                request.templateId(),
                writeJson(parameters),
                targetType,
                targetId,
                idempotencyKey
        );
    }

    private void assertPair(String first, String second, String firstName, String secondName) {
        if ((first == null) != (second == null)) {
            throw new BadRequestException(firstName + " and " + secondName + " must be provided together");
        }
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(field + " is required");
        }
        String normalized = value.trim();
        assertLength(normalized, field, maxLength);
        return normalized;
    }

    private String normalizeOptional(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        assertLength(normalized, field, maxLength);
        return normalized;
    }

    private void assertLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new BadRequestException(field + " must not exceed " + maxLength + " characters");
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("parameters must be valid JSON");
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored generation job parameters are invalid JSON", exception);
        }
    }

    private GenerationJobResponse responseWithHistory(GenerationJob job) {
        return toResponse(job, attemptRepository.findByJob_JobIdOrderByAttemptNumberAsc(job.getJobId()));
    }

    private GenerationJobResponse toResponse(GenerationJob job, List<GenerationJobAttempt> attempts) {
        List<GenerationJobAttemptResponse> attemptResponses = attempts.stream()
                .map(this::toAttemptResponse)
                .toList();
        UUID currentAttemptId = attempts.stream()
                .filter(attempt -> attempt.getAttemptNumber() == job.getAttemptCount())
                .map(GenerationJobAttempt::getAttemptId)
                .findFirst()
                .orElse(null);

        return new GenerationJobResponse(
                job.getJobId(),
                job.getJobType(),
                job.getStatus(),
                job.getOwnerId(),
                job.getIdempotencyKey(),
                job.getSourceType(),
                job.getSourceId(),
                job.getSourceAssetId(),
                job.getTemplateId(),
                parseJson(job.getParametersJson()),
                job.getTargetType(),
                job.getTargetId(),
                job.getProgress(),
                job.getCurrentStep(),
                job.getSafeErrorCode(),
                job.getSafeErrorMessage(),
                job.getResultReference(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                currentAttemptId,
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                attemptResponses
        );
    }

    private GenerationJobAttemptResponse toAttemptResponse(GenerationJobAttempt attempt) {
        return new GenerationJobAttemptResponse(
                attempt.getAttemptId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getProgress(),
                attempt.getCurrentStep(),
                attempt.getSafeErrorCode(),
                attempt.getSafeErrorMessage(),
                attempt.getResultReference(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt(),
                attempt.getStartedAt(),
                attempt.getFinishedAt()
        );
    }

    private record NormalizedCreate(
            JobType jobType,
            Integer ownerId,
            String sourceType,
            String sourceId,
            UUID sourceAssetId,
            UUID templateId,
            String parametersJson,
            String targetType,
            String targetId,
            String idempotencyKey
    ) {
    }
}
