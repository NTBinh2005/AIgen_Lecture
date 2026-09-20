package com.example.demo.service;

import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.request.PresentationUpdateRequest;
import com.example.demo.dto.response.PresentationExportResponse;
import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.dto.response.PresentationVersionResponse;
import com.example.demo.entity.Presentation;
import com.example.demo.entity.PresentationSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PresentationService {
    Page<PresentationResponse> list(Integer requesterId, String title, Pageable pageable);

    PresentationResponse get(UUID presentationId, Integer requesterId, boolean admin);

    List<PresentationVersionResponse> versions(
            UUID presentationId, Integer requesterId, boolean admin);

    PresentationResponse update(
            UUID presentationId,
            PresentationUpdateRequest request,
            Integer requesterId,
            boolean admin);

    PresentationResponse restore(
            UUID presentationId,
            UUID versionId,
            Integer requesterId,
            boolean admin);

    PresentationResponse publish(
            UUID presentationId,
            UUID versionId,
            Integer requesterId,
            boolean admin);

    void archive(UUID presentationId, Integer requesterId, boolean admin);

    List<Integer> collaborators(UUID presentationId, Integer requesterId, boolean admin);

    void addCollaborator(
            UUID presentationId,
            Integer collaboratorId,
            Integer requesterId,
            boolean admin);

    void removeCollaborator(
            UUID presentationId,
            Integer collaboratorId,
            Integer requesterId,
            boolean admin);

    PresentationExportResponse export(
            UUID presentationId,
            UUID versionId,
            Integer requesterId,
            boolean admin);

    PresentationExportContent downloadExport(
            UUID presentationId,
            UUID exportId,
            Integer requesterId,
            boolean admin);

    PresentationVersionResponse publishedVersionContract(UUID versionId);

    Presentation createGenerating(
            Integer ownerId,
            String title,
            PresentationSourceType sourceType,
            String sourceId,
            UUID sourceAssetId,
            String templateId,
            JsonNode parameters);

    PresentationResponse completeGeneration(
            UUID presentationId,
            Integer actorId,
            String title,
            List<PresentationSlideRequest> slides);

    void failGeneration(UUID presentationId);

    void validateTemplate(String templateId);
}
