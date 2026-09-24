package com.example.demo.dto.response;

import com.example.demo.entity.Asset;
import com.example.demo.entity.AssetPurpose;
import java.time.Instant;
import java.util.UUID;

/** Asset metadata response. Binary content is intentionally excluded. */
public record AssetResponse(
        UUID assetId,
        Integer ownerId,
        AssetPurpose purpose,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        String externalSourceUrl,
        String licenseStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        Long version
) {
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.getAssetId(),
                asset.getOwnerId(),
                asset.getPurpose(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getSha256(),
                asset.getExternalSourceUrl(),
                asset.getLicenseStatus(),
                asset.getCreatedAt(),
                asset.getUpdatedAt(),
                asset.getArchivedAt(),
                asset.getVersion()
        );
    }
}
