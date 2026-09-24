package com.example.demo.service;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.GenerationJobCreateRequest;
import com.example.demo.dto.request.PresentationCreateFromLectureRequest;
import com.example.demo.dto.response.AssetResponse;
import com.example.demo.dto.response.GenerationJobResponse;
import com.example.demo.dto.response.LectureVersionResponse;
import com.example.demo.dto.response.PresentationAsyncResponse;
import com.example.demo.entity.AssetPurpose;
import com.example.demo.entity.GenerationJob;
import com.example.demo.entity.JobStatus;
import com.example.demo.entity.JobType;
import com.example.demo.entity.Presentation;
import com.example.demo.entity.PresentationSourceType;
import com.example.demo.repository.GenerationJobRepository;
import com.example.demo.repository.PresentationRepository;
import com.example.demo.service.event.PresentationGenerationQueuedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PresentationGenerationWorkflowService {
    private final PresentationService presentationService;
    private final PresentationRepository presentationRepository;
    private final LectureService lectureService;
    private final AssetService assetService;
    private final GenerationJobRepository generationJobRepository;
    private final GenerationJobService generationJobService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public PresentationAsyncResponse fromLecture(
            Integer ownerId,
            boolean admin,
            PresentationCreateFromLectureRequest request,
            String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        presentationService.validateTemplate(request.templateId());
        LectureVersionResponse source = lectureService.getSourceVersionForPresentation(
                request.lectureVersionId(), ownerId, admin);
        JsonNode parameters = request.parameters() == null
                ? JsonNodeFactory.instance.objectNode()
                : objectMapper.valueToTree(request.parameters());
        String hash = hashStrings(
                request.title(),
                request.templateId(),
                request.lectureVersionId().toString(),
                canonical(parameters));

        GenerationJob existing = findExisting(ownerId, key);
        if (existing != null) {
            return existingResponse(existing, hash);
        }

        Presentation presentation = presentationService.createGenerating(
                ownerId,
                request.title(),
                PresentationSourceType.LECTURE_VERSION,
                source.versionId().toString(),
                null,
                request.templateId(),
                parameters);
        GenerationJobResponse job = createJob(
                presentation,
                ownerId,
                key,
                hash,
                "LECTURE_VERSION",
                source.versionId().toString(),
                null,
                request.templateId(),
                parameters);
        publish(job);
        return new PresentationAsyncResponse(presentation.getPresentationId(), job.jobId());
    }

    @Transactional
    public PresentationAsyncResponse fromFile(
            Integer ownerId,
            String title,
            String templateId,
            JsonNode parameters,
            MultipartFile file,
            String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        presentationService.validateTemplate(templateId);
        validatePresentationFilename(file == null ? null : file.getOriginalFilename());
        byte[] bytes = readBytes(file);
        JsonNode generationParameters = parameters == null
                ? JsonNodeFactory.instance.objectNode()
                : parameters;
        String hash = hashBytes(
                hashStrings(title, templateId, canonical(generationParameters)), bytes);

        GenerationJob existing = findExisting(ownerId, key);
        if (existing != null) {
            return existingResponse(existing, hash);
        }

        AssetResponse asset = assetService.upload(
                ownerId,
                AssetPurpose.PRESENTATION_SOURCE,
                file,
                null,
                null);
        Presentation presentation = presentationService.createGenerating(
                ownerId,
                title,
                PresentationSourceType.ASSET,
                null,
                asset.assetId(),
                templateId,
                generationParameters);
        GenerationJobResponse job = createJob(
                presentation,
                ownerId,
                key,
                hash,
                "ASSET",
                asset.assetId().toString(),
                asset.assetId(),
                templateId,
                generationParameters);
        publish(job);
        return new PresentationAsyncResponse(presentation.getPresentationId(), job.jobId());
    }

    private GenerationJobResponse createJob(
            Presentation presentation,
            Integer ownerId,
            String key,
            String requestHash,
            String sourceType,
            String sourceId,
            UUID sourceAssetId,
            String templateId,
            JsonNode requestedParameters) {
        ObjectNode jobParameters = objectMapper.createObjectNode();
        jobParameters.put("requestHash", requestHash);
        jobParameters.put("templateId", templateId);
        jobParameters.set("generationParameters", requestedParameters);
        return generationJobService.createOrGet(new GenerationJobCreateRequest(
                JobType.PRESENTATION_GENERATION,
                ownerId,
                sourceType,
                sourceId,
                sourceAssetId,
                null,
                jobParameters,
                "PRESENTATION",
                presentation.getPresentationId().toString(),
                key));
    }

    private GenerationJob findExisting(Integer ownerId, String key) {
        return generationJobRepository
                .findByOwnerIdAndJobTypeAndIdempotencyKey(
                        ownerId, JobType.PRESENTATION_GENERATION, key)
                .orElse(null);
    }

    private PresentationAsyncResponse existingResponse(GenerationJob job, String requestHash) {
        try {
            JsonNode parameters = objectMapper.readTree(job.getParametersJson());
            if (!requestHash.equals(parameters.path("requestHash").asText())) {
                throw new ConflictException("Idempotency key was used with a different request");
            }
        } catch (ConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored presentation job parameters are invalid", exception);
        }
        UUID presentationId = UUID.fromString(job.getTargetId());
        if (!presentationRepository.existsById(presentationId)) {
            throw new ResourceNotFoundException("Presentation for generation job is unavailable");
        }
        if (job.getStatus() == JobStatus.QUEUED) {
            publish(generationJobService.get(job.getJobId(), job.getOwnerId(), false));
        }
        return new PresentationAsyncResponse(presentationId, job.getJobId());
    }

    private void publish(GenerationJobResponse job) {
        eventPublisher.publishEvent(new PresentationGenerationQueuedEvent(
                job.jobId(),
                job.currentAttemptId(),
                UUID.fromString(job.targetId()),
                PresentationSourceType.valueOf(job.sourceType()),
                job.sourceId(),
                job.sourceAssetId(),
                job.ownerId()));
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        String normalized = key.trim();
        if (normalized.length() > 128) {
            throw new BadRequestException("Idempotency-Key must not exceed 128 characters");
        }
        return normalized;
    }

    private void validatePresentationFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BadRequestException("Presentation source filename is required");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".pdf") && !lower.endsWith(".docx")) {
            throw new BadRequestException("Presentation source must be a PDF or DOCX file");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Presentation source must not be empty");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Presentation source could not be read");
        }
    }

    private String canonical(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new BadRequestException("generation parameters must be valid JSON");
        }
    }

    private String hashStrings(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                if (value != null) {
                    digest.update(value.trim().getBytes(StandardCharsets.UTF_8));
                }
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String hashBytes(String prefixHash, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefixHash.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
