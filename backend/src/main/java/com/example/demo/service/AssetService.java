package com.example.demo.service;

import com.example.demo.dto.response.AssetResponse;
import com.example.demo.entity.AssetPurpose;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface AssetService {
    AssetResponse upload(
            Integer ownerId,
            AssetPurpose purpose,
            MultipartFile file,
            String externalSourceUrl,
            String licenseStatus
    );

    AssetResponse getMetadata(UUID assetId, Integer requesterId, boolean admin);

    AssetContent getContent(UUID assetId, Integer requesterId, boolean admin);

    void archive(UUID assetId, Integer requesterId, boolean admin);

    /**
     * Internal Backend 3 access for a processor running on behalf of an owner.
     * Archived assets cannot start new processing work.
     */
    AssetContent getContentForProcessing(UUID assetId, Integer expectedOwnerId);
}
