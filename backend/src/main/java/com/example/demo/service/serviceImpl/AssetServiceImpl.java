package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.AssetResponse;
import com.example.demo.entity.Asset;
import com.example.demo.entity.AssetPurpose;
import com.example.demo.repository.AssetRepository;
import com.example.demo.service.AssetContent;
import com.example.demo.service.AssetService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetServiceImpl implements AssetService {
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long ABSOLUTE_MAX_INFLATED_BYTES = 100L * 1024 * 1024;
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            "exe", "dll", "com", "scr", "msi", "jar", "class",
            "js", "jse", "vbs", "vbe", "cmd", "bat", "ps1", "hta"
    );

    private final AssetRepository assetRepository;
    private final long maxSourceFileSizeBytes;
    private final long maxImageFileSizeBytes;
    private final long maxExportFileSizeBytes;
    private final long maxOtherFileSizeBytes;

    public AssetServiceImpl(
            AssetRepository assetRepository,
            @Value("${app.asset.max-source-size-bytes:${app.asset.max-file-size-bytes:524288000}}")
            long maxSourceFileSizeBytes,
            @Value("${app.asset.max-image-size-bytes:10485760}") long maxImageFileSizeBytes,
            @Value("${app.asset.max-export-size-bytes:${app.asset.max-file-size-bytes:20971520}}")
            long maxExportFileSizeBytes,
            @Value("${app.asset.max-other-size-bytes:${app.asset.max-file-size-bytes:20971520}}")
            long maxOtherFileSizeBytes
    ) {
        if (maxSourceFileSizeBytes <= 0 || maxImageFileSizeBytes <= 0
                || maxExportFileSizeBytes <= 0 || maxOtherFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Asset size limits must be positive");
        }
        this.assetRepository = assetRepository;
        this.maxSourceFileSizeBytes = maxSourceFileSizeBytes;
        this.maxImageFileSizeBytes = maxImageFileSizeBytes;
        this.maxExportFileSizeBytes = maxExportFileSizeBytes;
        this.maxOtherFileSizeBytes = maxOtherFileSizeBytes;
    }

    @Override
    @Transactional
    public AssetResponse upload(
            Integer ownerId,
            AssetPurpose purpose,
            MultipartFile file,
            String externalSourceUrl,
            String licenseStatus
    ) {
        if (ownerId == null) {
            throw new BadRequestException("Asset owner is required");
        }
        if (purpose == null) {
            throw new BadRequestException("Asset purpose is required");
        }

        ValidatedFile validated = validateFile(file, purpose);
        String validatedSourceUrl = validateExternalSourceUrl(externalSourceUrl);
        String validatedLicenseStatus = validateOptionalText(licenseStatus, 100, "License status");

        Asset asset = new Asset();
        asset.setOwnerId(ownerId);
        asset.setPurpose(purpose);
        asset.setOriginalFilename(validated.filename());
        asset.setContentType(validated.type().contentType());
        asset.setSizeBytes((long) validated.bytes().length);
        asset.setSha256(sha256(validated.bytes()));
        asset.setBinaryContent(validated.bytes().clone());
        asset.setExternalSourceUrl(validatedSourceUrl);
        asset.setLicenseStatus(validatedLicenseStatus);

        return AssetResponse.from(assetRepository.save(asset));
    }

    @Override
    @Transactional(readOnly = true)
    public AssetResponse getMetadata(UUID assetId, Integer requesterId, boolean admin) {
        return AssetResponse.from(findAuthorized(assetId, requesterId, admin));
    }

    @Override
    @Transactional(readOnly = true)
    public AssetContent getContent(UUID assetId, Integer requesterId, boolean admin) {
        return AssetContent.from(findAuthorized(assetId, requesterId, admin));
    }

    @Override
    @Transactional
    public void archive(UUID assetId, Integer requesterId, boolean admin) {
        Asset asset = findAuthorized(assetId, requesterId, admin);
        asset.archive(Instant.now());
        assetRepository.save(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetContent getContentForProcessing(UUID assetId, Integer expectedOwnerId) {
        if (expectedOwnerId == null) {
            throw new AccessDeniedException("Asset owner context is required");
        }
        Asset asset = assetRepository.findByAssetIdAndArchivedAtIsNull(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));
        if (!expectedOwnerId.equals(asset.getOwnerId())) {
            throw new AccessDeniedException("You do not have permission to access this asset");
        }
        return AssetContent.from(asset);
    }

    private Asset findAuthorized(UUID assetId, Integer requesterId, boolean admin) {
        if (assetId == null) {
            throw new BadRequestException("Asset id is required");
        }
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));
        if (!admin && (requesterId == null || !requesterId.equals(asset.getOwnerId()))) {
            throw new AccessDeniedException("You do not have permission to access this asset");
        }
        return asset;
    }

    private ValidatedFile validateFile(MultipartFile file, AssetPurpose purpose) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Asset file must not be empty");
        }
        long maxFileSizeBytes = maxFileSizeFor(purpose);
        if (file.getSize() <= 0 || file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Asset file exceeds the configured size limit");
        }

        String filename = validateFilename(file.getOriginalFilename());
        FileType type = FileType.fromFilename(filename);
        validatePurpose(type, purpose);
        validateContentType(file.getContentType(), type);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Asset file could not be read");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("Asset file must not be empty");
        }
        if (bytes.length > maxFileSizeBytes) {
            throw new BadRequestException("Asset file exceeds the configured size limit");
        }

        validateMagic(bytes, type, maxFileSizeBytes);
        return new ValidatedFile(filename, type, bytes);
    }

    private long maxFileSizeFor(AssetPurpose purpose) {
        return switch (purpose) {
            case LECTURE_SOURCE, PRESENTATION_SOURCE -> maxSourceFileSizeBytes;
            case PRESENTATION_IMAGE -> maxImageFileSizeBytes;
            case PRESENTATION_EXPORT -> maxExportFileSizeBytes;
            case OTHER -> maxOtherFileSizeBytes;
        };
    }

    private String validateFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Asset filename is required");
        }
        String filename = originalFilename.trim();
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new BadRequestException("Asset filename is too long");
        }
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new BadRequestException("Asset filename contains an invalid path");
        }
        if (filename.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new BadRequestException("Asset filename contains control characters");
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw new BadRequestException("Asset filename must include a supported extension");
        }
        return filename;
    }

    private void validateContentType(String suppliedContentType, FileType type) {
        if (suppliedContentType == null || suppliedContentType.isBlank()) {
            throw new BadRequestException("Asset MIME type is required");
        }
        String normalized = suppliedContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!type.acceptedContentTypes().contains(normalized)) {
            throw new BadRequestException("Asset MIME type does not match its extension");
        }
    }

    private void validatePurpose(FileType type, AssetPurpose purpose) {
        boolean allowed = switch (purpose) {
            case LECTURE_SOURCE, PRESENTATION_SOURCE ->
                    type == FileType.PDF || type == FileType.DOCX || type == FileType.PPTX;
            case PRESENTATION_IMAGE -> type == FileType.PNG || type == FileType.JPEG;
            case PRESENTATION_EXPORT -> type == FileType.PPTX;
            case OTHER -> true;
        };
        if (!allowed) {
            throw new BadRequestException("Asset file type is not allowed for purpose " + purpose);
        }
    }

    private void validateMagic(byte[] bytes, FileType type, long maxFileSizeBytes) {
        switch (type) {
            case PDF -> validatePdf(bytes);
            case PNG -> validatePng(bytes);
            case JPEG -> validateJpeg(bytes);
            case DOCX, PPTX -> validateOfficeArchive(bytes, type, maxFileSizeBytes);
        }
    }

    private void validatePdf(byte[] bytes) {
        byte[] signature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        if (!startsWith(bytes, signature)) {
            throw new BadRequestException("PDF signature is invalid");
        }
        int tailStart = Math.max(0, bytes.length - 2048);
        String tail = new String(bytes, tailStart, bytes.length - tailStart, StandardCharsets.ISO_8859_1);
        if (!tail.contains("%%EOF")) {
            throw new BadRequestException("PDF file is incomplete");
        }

        String content = new String(bytes, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (Set.of("/javascript", "/js", "/launch", "/embeddedfile", "/openaction",
                        "/submitform", "/importdata")
                .stream().anyMatch(content::contains)) {
            throw new BadRequestException("PDF contains active or embedded content");
        }
    }

    private void validatePng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        boolean hasHeader = bytes.length >= 33
                && startsWith(bytes, signature)
                && bytes[12] == 'I' && bytes[13] == 'H' && bytes[14] == 'D' && bytes[15] == 'R';
        boolean hasEnd = bytes.length >= 12
                && bytes[bytes.length - 8] == 'I'
                && bytes[bytes.length - 7] == 'E'
                && bytes[bytes.length - 6] == 'N'
                && bytes[bytes.length - 5] == 'D';
        if (!hasHeader || !hasEnd) {
            throw new BadRequestException("PNG signature is invalid or incomplete");
        }
    }

    private void validateJpeg(byte[] bytes) {
        boolean valid = bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF
                && (bytes[bytes.length - 2] & 0xFF) == 0xFF
                && (bytes[bytes.length - 1] & 0xFF) == 0xD9;
        if (!valid) {
            throw new BadRequestException("JPEG signature is invalid or incomplete");
        }
    }

    private void validateOfficeArchive(byte[] bytes, FileType type, long maxFileSizeBytes) {
        if (bytes.length < 4
                || bytes[0] != 'P' || bytes[1] != 'K'
                || bytes[2] != 0x03 || bytes[3] != 0x04) {
            throw new BadRequestException("Office document is not a valid OOXML archive");
        }

        Set<String> entries = new HashSet<>();
        long inflatedBytes = 0;
        long maxInflatedBytes = Math.min(safeMultiply(maxFileSizeBytes, 5), ABSOLUTE_MAX_INFLATED_BYTES);
        String contentTypesXml = null;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            int entryCount = 0;

            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("Office archive contains too many entries");
                }

                String entryName = validateZipEntryName(entry.getName());
                String normalizedName = entryName.toLowerCase(Locale.ROOT);
                if (!entries.add(normalizedName)) {
                    throw new BadRequestException("Office archive contains duplicate entries");
                }
                rejectSuspiciousEntry(normalizedName);

                ByteArrayOutputStream contentTypes = "[content_types].xml".equals(normalizedName)
                        ? new ByteArrayOutputStream()
                        : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    inflatedBytes += read;
                    if (inflatedBytes > maxInflatedBytes) {
                        throw new BadRequestException("Office archive expands beyond the safe limit");
                    }
                    if (contentTypes != null) {
                        contentTypes.write(buffer, 0, read);
                    }
                }
                if (contentTypes != null) {
                    contentTypesXml = contentTypes.toString(StandardCharsets.UTF_8);
                }
                zip.closeEntry();
            }
        } catch (ZipException ex) {
            throw new BadRequestException("Office document archive is malformed");
        } catch (IOException ex) {
            throw new BadRequestException("Office document could not be inspected");
        }

        String requiredPart = type == FileType.DOCX ? "word/document.xml" : "ppt/presentation.xml";
        if (!entries.contains("[content_types].xml") || !entries.contains(requiredPart)) {
            throw new BadRequestException("Office document content does not match its extension");
        }
        if (contentTypesXml == null) {
            throw new BadRequestException("Office document content types are missing");
        }
        String normalizedContentTypes = contentTypesXml.toLowerCase(Locale.ROOT);
        if (normalizedContentTypes.contains("macroenabled")
                || normalizedContentTypes.contains("vbaproject")) {
            throw new BadRequestException("Macro-enabled Office documents are not allowed");
        }

        // A high expansion ratio is a strong indication of a ZIP bomb. Small
        // files get a little headroom to avoid rejecting normal OOXML XML.
        long ratioAllowance = Math.max(1_000_000L, safeMultiply(bytes.length, 200));
        if (inflatedBytes > ratioAllowance) {
            throw new BadRequestException("Office archive compression ratio is unsafe");
        }
    }

    private String validateZipEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()
                || entryName.startsWith("/") || entryName.startsWith("\\")
                || entryName.contains("\\") || entryName.contains(":")
                || entryName.equals("..") || entryName.startsWith("../")
                || entryName.contains("/../")) {
            throw new BadRequestException("Office archive contains an unsafe path");
        }
        if (entryName.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new BadRequestException("Office archive contains an invalid entry name");
        }
        return entryName;
    }

    private void rejectSuspiciousEntry(String normalizedName) {
        if (normalizedName.contains("vbaproject")
                || normalizedName.contains("/macros/")
                || normalizedName.contains("/activex/")
                || normalizedName.contains("/embeddings/")
                || normalizedName.equals("encryptedpackage")
                || normalizedName.equals("encryptioninfo")) {
            throw new BadRequestException("Office archive contains active or encrypted content");
        }

        int dot = normalizedName.lastIndexOf('.');
        if (dot >= 0 && dot < normalizedName.length() - 1) {
            String extension = normalizedName.substring(dot + 1);
            if (EXECUTABLE_EXTENSIONS.contains(extension)
                    || Set.of("zip", "rar", "7z", "tar", "gz").contains(extension)) {
                throw new BadRequestException("Office archive contains a suspicious embedded file");
            }
        }
    }

    private String validateExternalSourceUrl(String value) {
        String normalized = validateOptionalText(value, 2048, "External source URL");
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || uri.getUserInfo() != null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                throw new BadRequestException("External source URL must be an HTTP(S) URL without credentials");
            }
            return normalized;
        } catch (URISyntaxException ex) {
            throw new BadRequestException("External source URL is invalid");
        }
    }

    private String validateOptionalText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BadRequestException(fieldName + " is too long");
        }
        if (normalized.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new BadRequestException(fieldName + " contains control characters");
        }
        return normalized;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }

    private long safeMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private record ValidatedFile(String filename, FileType type, byte[] bytes) {
    }

    private enum FileType {
        PDF(Set.of("pdf"), Set.of("application/pdf"), "application/pdf"),
        DOCX(
                Set.of("docx"),
                Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ),
        PPTX(
                Set.of("pptx"),
                Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ),
        PNG(Set.of("png"), Set.of("image/png"), "image/png"),
        JPEG(Set.of("jpg", "jpeg"), Set.of("image/jpeg"), "image/jpeg");

        private final Set<String> extensions;
        private final Set<String> acceptedContentTypes;
        private final String contentType;

        FileType(Set<String> extensions, Set<String> acceptedContentTypes, String contentType) {
            this.extensions = extensions;
            this.acceptedContentTypes = acceptedContentTypes;
            this.contentType = contentType;
        }

        static FileType fromFilename(String filename) {
            String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(type -> type.extensions.contains(extension))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Unsupported asset type. Allowed: PDF, DOCX, PPTX, PNG, JPEG"));
        }

        Set<String> acceptedContentTypes() {
            return acceptedContentTypes;
        }

        String contentType() {
            return contentType;
        }
    }
}
