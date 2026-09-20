package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.request.PresentationUpdateRequest;
import com.example.demo.dto.response.PresentationExportResponse;
import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.dto.response.PresentationSlideResponse;
import com.example.demo.dto.response.PresentationVersionResponse;
import com.example.demo.entity.Presentation;
import com.example.demo.entity.PresentationCollaborator;
import com.example.demo.entity.PresentationExport;
import com.example.demo.entity.PresentationSourceType;
import com.example.demo.entity.PresentationStatus;
import com.example.demo.entity.PresentationVersion;
import com.example.demo.entity.PresentationVersionStatus;
import com.example.demo.repository.PresentationCollaboratorRepository;
import com.example.demo.repository.PresentationExportRepository;
import com.example.demo.repository.PresentationRepository;
import com.example.demo.repository.PresentationVersionRepository;
import com.example.demo.service.PresentationExportContent;
import com.example.demo.service.PresentationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PresentationServiceImpl implements PresentationService {
    private static final String PPTX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private final PresentationRepository presentationRepository;
    private final PresentationVersionRepository versionRepository;
    private final PresentationCollaboratorRepository collaboratorRepository;
    private final PresentationExportRepository exportRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.presentation.allowed-templates:standard,academic,minimal}")
    private List<String> allowedTemplates;

    @Override
    @Transactional(readOnly = true)
    public Page<PresentationResponse> list(
            Integer requesterId,
            String title,
            Pageable pageable) {
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : null;
        return presentationRepository.findVisibleTo(requesterId, normalizedTitle, pageable)
                .map(presentation -> toResponse(presentation, requesterId, false));
    }

    @Override
    @Transactional(readOnly = true)
    public PresentationResponse get(UUID presentationId, Integer requesterId, boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        return toResponse(presentation, requesterId, admin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PresentationVersionResponse> versions(
            UUID presentationId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        return versionRepository
                .findByPresentation_PresentationIdOrderByVersionNumberDesc(presentationId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Override
    @Transactional
    public PresentationResponse update(
            UUID presentationId,
            PresentationUpdateRequest request,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        assertMutable(presentation);
        List<PresentationSlideResponse> slides = normalizeSlides(request.slides());
        String title = normalizeTitle(request.title());

        PresentationVersion version = createVersion(
                presentation,
                title,
                slides,
                requesterId,
                null);
        presentation.setTitle(title);
        presentation.setCurrentVersionId(version.getPresentationVersionId());
        presentation.setCurrentVersionNumber(version.getVersionNumber());
        if (presentation.getStatus() != PresentationStatus.PUBLISHED) {
            presentation.setStatus(PresentationStatus.DRAFT);
        }
        presentationRepository.save(presentation);
        return toResponse(presentation, requesterId, admin);
    }

    @Override
    @Transactional
    public PresentationResponse restore(
            UUID presentationId,
            UUID versionId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        assertMutable(presentation);
        PresentationVersion source = findVersion(presentationId, versionId);
        PresentationVersion restored = createVersion(
                presentation,
                source.getTitle(),
                readSlides(source.getSlidesJson()),
                requesterId,
                source.getPresentationVersionId());
        presentation.setTitle(restored.getTitle());
        presentation.setCurrentVersionId(restored.getPresentationVersionId());
        presentation.setCurrentVersionNumber(restored.getVersionNumber());
        if (presentation.getStatus() != PresentationStatus.PUBLISHED) {
            presentation.setStatus(PresentationStatus.DRAFT);
        }
        presentationRepository.save(presentation);
        return toResponse(presentation, requesterId, admin);
    }

    @Override
    @Transactional
    public PresentationResponse publish(
            UUID presentationId,
            UUID versionId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertOwnerOrAdmin(presentation, requesterId, admin);
        assertMutable(presentation);
        UUID selectedId = versionId == null ? presentation.getCurrentVersionId() : versionId;
        PresentationVersion selected = findVersion(presentationId, selectedId);
        List<PresentationSlideResponse> slides = readSlides(selected.getSlidesJson());
        if (!StringUtils.hasText(selected.getTitle()) || slides.isEmpty()) {
            throw new BadRequestException("A title and at least one slide are required before publishing");
        }

        if (presentation.getPublishedVersionId() != null
                && !presentation.getPublishedVersionId().equals(selected.getPresentationVersionId())) {
            versionRepository.findById(presentation.getPublishedVersionId()).ifPresent(previous -> {
                previous.setStatus(PresentationVersionStatus.SUPERSEDED);
                versionRepository.save(previous);
            });
        }
        Instant now = Instant.now();
        selected.setStatus(PresentationVersionStatus.PUBLISHED);
        selected.setPublishedAt(now);
        versionRepository.save(selected);
        presentation.setTitle(selected.getTitle());
        presentation.setStatus(PresentationStatus.PUBLISHED);
        presentation.setPublishedVersionId(selected.getPresentationVersionId());
        presentation.setCurrentVersionId(selected.getPresentationVersionId());
        presentation.setCurrentVersionNumber(selected.getVersionNumber());
        presentation.setPublishedAt(now);
        presentationRepository.save(presentation);
        return toResponse(presentation, requesterId, admin);
    }

    @Override
    @Transactional
    public void archive(UUID presentationId, Integer requesterId, boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertOwnerOrAdmin(presentation, requesterId, admin);
        if (presentation.getArchivedAt() == null) {
            presentation.setStatus(PresentationStatus.ARCHIVED);
            presentation.setArchivedAt(Instant.now());
            presentationRepository.save(presentation);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> collaborators(
            UUID presentationId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        return collaboratorRepository
                .findByPresentation_PresentationIdOrderByCreatedAtAsc(presentationId)
                .stream()
                .map(PresentationCollaborator::getCollaboratorUserId)
                .toList();
    }

    @Override
    @Transactional
    public void addCollaborator(
            UUID presentationId,
            Integer collaboratorId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertOwnerOrAdmin(presentation, requesterId, admin);
        if (Objects.equals(presentation.getOwnerId(), collaboratorId)) {
            throw new ConflictException("The owner is already an editor");
        }
        if (collaboratorRepository
                .existsByPresentation_PresentationIdAndCollaboratorUserId(
                        presentationId, collaboratorId)) {
            return;
        }
        PresentationCollaborator collaborator = new PresentationCollaborator();
        collaborator.setPresentation(presentation);
        collaborator.setCollaboratorUserId(collaboratorId);
        collaborator.setCreatedBy(requesterId);
        collaboratorRepository.save(collaborator);
    }

    @Override
    @Transactional
    public void removeCollaborator(
            UUID presentationId,
            Integer collaboratorId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertOwnerOrAdmin(presentation, requesterId, admin);
        collaboratorRepository.deleteByPresentation_PresentationIdAndCollaboratorUserId(
                presentationId, collaboratorId);
    }

    @Override
    @Transactional
    public PresentationExportResponse export(
            UUID presentationId,
            UUID versionId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        PresentationVersion version = findVersion(presentationId, versionId);
        PresentationExport existing = exportRepository
                .findByPresentationVersion_PresentationVersionId(versionId)
                .orElse(null);
        if (existing != null) {
            return toExportResponse(existing);
        }

        byte[] bytes = createPptx(version);
        PresentationExport export = new PresentationExport();
        export.setPresentation(presentation);
        export.setPresentationVersion(version);
        export.setFileName(safeFileName(version.getTitle()) + "-v" + version.getVersionNumber() + ".pptx");
        export.setContentType(PPTX_CONTENT_TYPE);
        export.setFileBytes(bytes);
        export.setByteSize(bytes.length);
        export.setSha256(sha256(bytes));
        export.setRequestedBy(requesterId);
        return toExportResponse(exportRepository.save(export));
    }

    @Override
    @Transactional(readOnly = true)
    public PresentationExportContent downloadExport(
            UUID presentationId,
            UUID exportId,
            Integer requesterId,
            boolean admin) {
        Presentation presentation = findPresentation(presentationId);
        assertEditorOrAdmin(presentation, requesterId, admin);
        PresentationExport export = exportRepository.findById(exportId)
                .filter(candidate -> candidate.getPresentation().getPresentationId().equals(presentationId))
                .orElseThrow(() -> new ResourceNotFoundException("Presentation export not found: " + exportId));
        return PresentationExportContent.from(export);
    }

    @Override
    @Transactional(readOnly = true)
    public PresentationVersionResponse publishedVersionContract(UUID versionId) {
        PresentationVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation version not found: " + versionId));
        if (version.getStatus() != PresentationVersionStatus.PUBLISHED) {
            throw new ConflictException("Presentation version is not published");
        }
        return toVersionResponse(version);
    }

    @Override
    @Transactional
    public Presentation createGenerating(
            Integer ownerId,
            String title,
            PresentationSourceType sourceType,
            String sourceId,
            UUID sourceAssetId,
            String templateId,
            JsonNode parameters) {
        validateTemplate(templateId);
        if (sourceType == null) {
            throw new BadRequestException("sourceType is required");
        }
        if (sourceType == PresentationSourceType.LECTURE_VERSION && !StringUtils.hasText(sourceId)) {
            throw new BadRequestException("A LectureVersion source is required");
        }
        if (sourceType == PresentationSourceType.ASSET && sourceAssetId == null) {
            throw new BadRequestException("A source Asset is required");
        }
        Presentation presentation = new Presentation();
        presentation.setOwnerId(ownerId);
        presentation.setTitle(normalizeTitle(title));
        presentation.setSourceType(sourceType);
        presentation.setSourceId(sourceId);
        presentation.setSourceAssetId(sourceAssetId);
        presentation.setTemplateId(templateId.trim());
        presentation.setGenerationParameters(writeJson(parameters == null
                ? JsonNodeFactory.instance.objectNode()
                : parameters));
        presentation.setStatus(PresentationStatus.GENERATING);
        return presentationRepository.save(presentation);
    }

    @Override
    @Transactional
    public PresentationResponse completeGeneration(
            UUID presentationId,
            Integer actorId,
            String title,
            List<PresentationSlideRequest> slideRequests) {
        Presentation presentation = findPresentation(presentationId);
        List<PresentationSlideResponse> slides = normalizeSlides(slideRequests);
        PresentationVersion version = createVersion(
                presentation,
                normalizeTitle(title),
                slides,
                actorId,
                null);
        presentation.setTitle(version.getTitle());
        presentation.setCurrentVersionId(version.getPresentationVersionId());
        presentation.setCurrentVersionNumber(version.getVersionNumber());
        presentation.setStatus(PresentationStatus.READY);
        presentationRepository.save(presentation);
        return toResponse(presentation, actorId, false);
    }

    @Override
    @Transactional
    public void failGeneration(UUID presentationId) {
        presentationRepository.findById(presentationId).ifPresent(presentation -> {
            if (presentation.getCurrentVersionId() == null) {
                presentation.setStatus(PresentationStatus.FAILED);
                presentationRepository.save(presentation);
            }
        });
    }

    @Override
    public void validateTemplate(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            throw new BadRequestException("templateId is required");
        }
        boolean supported = allowedTemplates.stream()
                .map(String::trim)
                .anyMatch(templateId.trim()::equalsIgnoreCase);
        if (!supported) {
            throw new BadRequestException("Template is not compatible with generated presentations");
        }
    }

    private PresentationVersion createVersion(
            Presentation presentation,
            String title,
            List<PresentationSlideResponse> slides,
            Integer actorId,
            UUID restoredFrom) {
        PresentationVersion version = new PresentationVersion();
        version.setPresentation(presentation);
        version.setVersionNumber(presentation.getCurrentVersionNumber() + 1);
        version.setTitle(title);
        version.setSlidesJson(writeJson(slides));
        version.setStatus(PresentationVersionStatus.DRAFT);
        version.setRestoredFromVersionId(restoredFrom);
        version.setCreatedBy(actorId);
        return versionRepository.save(version);
    }

    private PresentationResponse toResponse(
            Presentation presentation,
            Integer requesterId,
            boolean admin) {
        PresentationVersion current = presentation.getCurrentVersionId() == null
                ? null
                : versionRepository.findById(presentation.getCurrentVersionId()).orElse(null);
        boolean editor = admin || isOwner(presentation, requesterId)
                || collaboratorRepository
                        .existsByPresentation_PresentationIdAndCollaboratorUserId(
                                presentation.getPresentationId(), requesterId);
        boolean mutable = presentation.getArchivedAt() == null;
        return new PresentationResponse(
                presentation.getPresentationId(),
                presentation.getOwnerId(),
                presentation.getTitle(),
                presentation.getSourceType(),
                presentation.getSourceId(),
                presentation.getSourceAssetId(),
                presentation.getTemplateId(),
                readJson(presentation.getGenerationParameters()),
                presentation.getStatus(),
                presentation.getCurrentVersionId(),
                presentation.getPublishedVersionId(),
                presentation.getCurrentVersionNumber(),
                presentation.isSavedToLibrary(),
                presentation.getCreatedAt(),
                presentation.getUpdatedAt(),
                presentation.getPublishedAt(),
                current == null ? null : toVersionResponse(current),
                editor && mutable,
                (admin || isOwner(presentation, requesterId)) && mutable,
                (admin || isOwner(presentation, requesterId)) && mutable,
                editor);
    }

    private PresentationVersionResponse toVersionResponse(PresentationVersion version) {
        return new PresentationVersionResponse(
                version.getPresentationVersionId(),
                version.getVersionNumber(),
                version.getTitle(),
                readSlides(version.getSlidesJson()),
                version.getStatus(),
                version.getRestoredFromVersionId(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getUpdatedAt(),
                version.getPublishedAt());
    }

    private PresentationExportResponse toExportResponse(PresentationExport export) {
        return new PresentationExportResponse(
                export.getPresentationExportId(),
                export.getPresentation().getPresentationId(),
                export.getPresentationVersion().getPresentationVersionId(),
                export.getFileName(),
                export.getContentType(),
                export.getByteSize(),
                export.getSha256(),
                export.getCreatedAt());
    }

    private List<PresentationSlideResponse> normalizeSlides(List<PresentationSlideRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("At least one slide is required");
        }
        Set<Integer> orders = new HashSet<>();
        List<PresentationSlideResponse> result = new ArrayList<>();
        for (PresentationSlideRequest request : requests) {
            if (request == null || !orders.add(request.order())) {
                throw new BadRequestException("Slide order values must be unique");
            }
            String layout = StringUtils.hasText(request.layout())
                    ? request.layout().trim().toUpperCase()
                    : "TITLE_AND_CONTENT";
            result.add(new PresentationSlideResponse(
                    normalizeTitle(request.title()),
                    request.contentBlocks().stream().map(String::trim).toList(),
                    StringUtils.hasText(request.speakerNotes()) ? request.speakerNotes().trim() : null,
                    layout,
                    request.order()));
        }
        result.sort(Comparator.comparingInt(PresentationSlideResponse::order));
        return List.copyOf(result);
    }

    private byte[] createPptx(PresentationVersion version) {
        List<PresentationSlideResponse> slides = readSlides(version.getSlidesJson());
        if (slides.isEmpty()) {
            throw new BadRequestException("The selected version has no slides to export");
        }
        try (XMLSlideShow pptx = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pptx.setPageSize(new Dimension(720, 405));
            for (PresentationSlideResponse source : slides) {
                XSLFSlide slide = pptx.createSlide();
                addTitle(slide, source.title());
                addContent(slide, source.contentBlocks());
                addNotes(slide, source.speakerNotes());
            }
            pptx.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Presentation export could not be created", exception);
        }
    }

    private void addTitle(XSLFSlide slide, String title) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(45, 28, 630, 70));
        XSLFTextRun run = box.addNewTextParagraph().addNewTextRun();
        run.setText(title);
        run.setFontSize(28.0);
        run.setBold(true);
        run.setFontColor(new Color(30, 55, 90));
    }

    private void addContent(XSLFSlide slide, List<String> blocks) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(65, 115, 590, 350));
        for (String block : blocks) {
            XSLFTextParagraph paragraph = box.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setIndent(22.0);
            paragraph.setLeftMargin(10.0);
            paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(block);
            run.setFontSize(20.0);
        }
    }

    private void addNotes(XSLFSlide slide, String speakerNotes) {
        if (!StringUtils.hasText(speakerNotes)) {
            return;
        }
        XMLSlideShow slideShow = slide.getSlideShow();
        if (slideShow.getNotesMaster() == null) {
            slideShow.createNotesMaster();
        }
        XSLFNotes notes = slideShow.getNotesSlide(slide);
        if (notes == null) {
            return;
        }
        XSLFTextBox noteBox = notes.createTextBox();
        noteBox.setAnchor(new Rectangle2D.Double(25, 25, 600, 350));
        noteBox.setText(speakerNotes);
    }

    private Presentation findPresentation(UUID presentationId) {
        return presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Presentation not found: " + presentationId));
    }

    private PresentationVersion findVersion(UUID presentationId, UUID versionId) {
        if (versionId == null) {
            throw new ResourceNotFoundException("Presentation version is not available");
        }
        return versionRepository.findById(versionId)
                .filter(version -> version.getPresentation().getPresentationId().equals(presentationId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Presentation version not found: " + versionId));
    }

    private void assertEditorOrAdmin(
            Presentation presentation,
            Integer requesterId,
            boolean admin) {
        if (admin || isOwner(presentation, requesterId)
                || collaboratorRepository
                        .existsByPresentation_PresentationIdAndCollaboratorUserId(
                                presentation.getPresentationId(), requesterId)) {
            return;
        }
        throw new AccessDeniedException("You cannot access this presentation");
    }

    private void assertOwnerOrAdmin(
            Presentation presentation,
            Integer requesterId,
            boolean admin) {
        if (!admin && !isOwner(presentation, requesterId)) {
            throw new AccessDeniedException("Only the presentation owner can perform this action");
        }
    }

    private boolean isOwner(Presentation presentation, Integer requesterId) {
        return Objects.equals(presentation.getOwnerId(), requesterId);
    }

    private void assertMutable(Presentation presentation) {
        if (presentation.getArchivedAt() != null
                || presentation.getStatus() == PresentationStatus.ARCHIVED) {
            throw new ConflictException("Archived presentations are read-only");
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

    private String safeFileName(String title) {
        String safe = title.replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.isBlank()) {
            safe = "presentation";
        }
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Presentation data is not valid JSON");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored presentation parameters are invalid", exception);
        }
    }

    private List<PresentationSlideResponse> readSlides(String json) {
        try {
            return objectMapper.readValue(
                    json == null ? "[]" : json,
                    new TypeReference<List<PresentationSlideResponse>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored presentation slides are invalid", exception);
        }
    }
}
