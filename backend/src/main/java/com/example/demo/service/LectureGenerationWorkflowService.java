package com.example.demo.service;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.GenerationJobCreateRequest;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.response.AssetResponse;
import com.example.demo.dto.response.GenerationJobResponse;
import com.example.demo.dto.response.LectureAsyncResponse;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.entity.AssetPurpose;
import com.example.demo.entity.GenerationJob;
import com.example.demo.entity.JobStatus;
import com.example.demo.entity.JobType;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.LectureAccessScope;
import com.example.demo.entity.LectureStatus;
import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import com.example.demo.repository.GenerationJobRepository;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.LectureVersionRepository;
import com.example.demo.service.event.LectureGenerationQueuedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LectureGenerationWorkflowService {
    private final AssetService assetService;
    private final LectureService lectureService;
    private final LectureRepository lectureRepository;
    private final LectureVersionRepository lectureVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GenerationJobService generationJobService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public LectureAsyncResponse start(
            Integer ownerId,
            String title,
            LectureAccessScope accessScope,
            MultipartFile file,
            String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String normalizedTitle = normalizeTitle(title);
        byte[] requestBytes = readBytes(file);
        String requestHash = requestHash(
                normalizedTitle,
                accessScope == null ? LectureAccessScope.PRIVATE : accessScope,
                file == null ? null : file.getOriginalFilename(),
                requestBytes);

        GenerationJob existing = generationJobRepository
                .findByOwnerIdAndJobTypeAndIdempotencyKey(
                        ownerId, JobType.LECTURE_GENERATION, normalizedKey)
                .orElse(null);
        if (existing != null) {
            assertSameRequest(existing, requestHash);
            Lecture lecture = lectureRepository.findById(parseLectureId(existing.getTargetId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Lecture for generation job is no longer available"));
            if (existing.getStatus() == JobStatus.QUEUED) {
                GenerationJobResponse response = generationJobService.get(
                        existing.getJobId(), ownerId, false);
                publish(response);
            }
            return new LectureAsyncResponse(
                    lecture.getLectureId(), lecture.getBusinessId(), existing.getJobId());
        }

        AssetResponse asset = assetService.upload(
                ownerId,
                AssetPurpose.LECTURE_SOURCE,
                file,
                null,
                null);

        LectureCreateRequest lectureRequest = new LectureCreateRequest();
        lectureRequest.setTitle(normalizedTitle);
        lectureRequest.setAccessScope(accessScope == null ? LectureAccessScope.PRIVATE : accessScope);
        LectureResponse created = lectureService.createLecture(ownerId, lectureRequest);

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("requestHash", requestHash);
        parameters.put("filename", asset.originalFilename());
        GenerationJobResponse job = generationJobService.createOrGet(new GenerationJobCreateRequest(
                JobType.LECTURE_GENERATION,
                ownerId,
                "ASSET",
                asset.assetId().toString(),
                asset.assetId(),
                null,
                parameters,
                "LECTURE",
                created.getLectureId().toString(),
                normalizedKey));

        Lecture lecture = lectureRepository.findById(created.getLectureId())
                .orElseThrow(() -> new ResourceNotFoundException("Lecture not found after creation"));
        lecture.setSourceAssetId(asset.assetId());
        lecture.setLatestGenerationJobId(job.jobId());
        lecture.setStatus(LectureStatus.PROCESSING);
        lectureRepository.save(lecture);
        lectureVersionRepository.findById(lecture.getCurrentVersionId()).ifPresent(version -> {
            version.setSourceAssetId(asset.assetId());
            version.setAiGenerated(true);
            version.setStatus(LectureVersionStatus.DRAFT);
            lectureVersionRepository.save(version);
        });

        publish(job);
        return new LectureAsyncResponse(lecture.getLectureId(), lecture.getBusinessId(), job.jobId());
    }

    private void publish(GenerationJobResponse job) {
        if (job.currentAttemptId() == null || job.sourceAssetId() == null || job.targetId() == null) {
            throw new IllegalStateException("Lecture generation job is missing its processing references");
        }
        eventPublisher.publishEvent(new LectureGenerationQueuedEvent(
                job.jobId(),
                job.currentAttemptId(),
                Long.valueOf(job.targetId()),
                job.sourceAssetId(),
                job.ownerId()));
    }

    private void assertSameRequest(GenerationJob job, String requestHash) {
        try {
            JsonNode stored = objectMapper.readTree(job.getParametersJson());
            if (!requestHash.equals(stored.path("requestHash").asText())) {
                throw new ConflictException("Idempotency key was used with a different upload request");
            }
        } catch (ConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored lecture generation request is invalid", exception);
        }
    }

    private Long parseLectureId(String targetId) {
        try {
            return Long.valueOf(targetId);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Generation job target is not a lecture id", exception);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Source document must not be empty");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Source document could not be read");
        }
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BadRequestException("title is required");
        }
        String normalized = title.trim();
        if (normalized.length() > 255) {
            throw new BadRequestException("title must not exceed 255 characters");
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        String normalized = key.trim();
        if (normalized.length() > 128) {
            throw new BadRequestException("Idempotency-Key must not exceed 128 characters");
        }
        return normalized;
    }

    private String requestHash(
            String title,
            LectureAccessScope accessScope,
            String filename,
            byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(title.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(accessScope.name().getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            if (filename != null) {
                digest.update(filename.getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
