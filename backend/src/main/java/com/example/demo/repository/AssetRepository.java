package com.example.demo.repository;

import com.example.demo.entity.Asset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByAssetIdAndArchivedAtIsNull(UUID assetId);
}
