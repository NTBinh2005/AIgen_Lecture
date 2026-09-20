package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.LearningAccessResponse;
import com.example.demo.entity.LearningAccess;
import com.example.demo.repository.LearningAccessRepository;
import com.example.demo.service.LearningAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningAccessServiceImpl implements LearningAccessService {

    private final LearningAccessRepository learningAccessRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LearningAccessResponse> getMyAccess(Integer userId) {
        return learningAccessRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAccess(Integer userId, Integer productId) {
        return learningAccessRepository.existsByUserUserIdAndProductId(userId, productId);
    }

    private LearningAccessResponse toResponse(LearningAccess access) {
        return new LearningAccessResponse(
                access.getId(),
                access.getUser().getUserId(),
                access.getProductId(),
                access.getPaymentId(),
                access.getAccessType(),
                access.getExpiresAt(),
                access.getGrantedAt()
        );
    }
}
