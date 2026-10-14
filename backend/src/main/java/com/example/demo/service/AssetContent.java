package com.example.demo.service;

import com.example.demo.entity.Asset;
import com.example.demo.entity.AssetPurpose;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal binary transfer object for Backend 3 processors and downloads.
 * It intentionally has no generated toString method and defensively copies
 * the byte array so file contents cannot leak through logging or mutation.
 */
public final class AssetContent {
    private final UUID assetId;
    private final Integer ownerId;
    private final AssetPurpose purpose;
    private final String originalFilename;
    private final String contentType;
    private final long sizeBytes;
    private final String sha256;
    private final String externalSourceUrl;
    private final String licenseStatus;
    private final Instant archivedAt;
    private final byte[] bytes;

    private AssetContent(Asset asset) {
        this.assetId = asset.getAssetId();
        this.ownerId = asset.getOwnerId();
        this.purpose = asset.getPurpose();
        this.originalFilename = asset.getOriginalFilename();
        this.contentType = asset.getContentType();
        this.sizeBytes = asset.getSizeBytes();
        this.sha256 = asset.getSha256();
        this.externalSourceUrl = asset.getExternalSourceUrl();
        this.licenseStatus = asset.getLicenseStatus();
        this.archivedAt = asset.getArchivedAt();
        this.bytes = asset.getBinaryContent().clone();
    }

    public static AssetContent from(Asset asset) {
        return new AssetContent(asset);
    }

    public UUID getAssetId() {
        return assetId;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public AssetPurpose getPurpose() {
        return purpose;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getExternalSourceUrl() {
        return externalSourceUrl;
    }

    public String getLicenseStatus() {
        return licenseStatus;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }
}
