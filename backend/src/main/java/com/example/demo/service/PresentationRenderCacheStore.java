package com.example.demo.service;

import com.example.demo.entity.PresentationRenderCache;
import com.example.demo.repository.PresentationRenderCacheRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc/ghi HTML trình chiếu đã dựng. Transaction riêng để dùng được cả khi người gọi đang ở
 * transaction chỉ đọc (học sinh xem trình chiếu). Người gọi tự bắt lỗi ghi.
 */
@Component
@RequiredArgsConstructor
public class PresentationRenderCacheStore {
    private final PresentationRenderCacheRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<String> find(String cacheKey) {
        return repository.findById(cacheKey).map(PresentationRenderCache::getHtml);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String cacheKey, UUID versionId, String theme, String html) {
        PresentationRenderCache entry = repository.findById(cacheKey).orElseGet(PresentationRenderCache::new);
        if (html.equals(entry.getHtml())) {
            return;
        }
        entry.setCacheKey(cacheKey);
        entry.setVersionId(versionId);
        entry.setTheme(theme);
        entry.setHtml(html);
        entry.setUpdatedAt(Instant.now());
        repository.saveAndFlush(entry);
    }
}
