package com.example.demo.service;

import com.example.demo.dto.response.RecordingResponse;
import com.example.demo.entity.RecordingStatus;

/**
 * LIVE-07: Ghi hình — tương tác với provider và xử lý webhook recording.
 */
public interface RecordingService {

    /**
     * Yêu cầu provider ghi hình — tạo SessionRecording với status REQUESTED.
     * LIVE-BR-04: consent phải được ghi nhận trước khi gọi hàm này.
     */
    RecordingResponse requestRecording(Long sessionId, Integer teacherId);

    RecordingResponse getRecording(Long sessionId, Integer currentUserId);

    /**
     * Cập nhật trạng thái recording từ webhook provider.
     * Xử lý idempotent: không update nếu trạng thái đã là READY/FAILED.
     */
    void handleWebhookUpdate(String externalRecordingId, RecordingStatus newStatus,
                             String playbackUrl, Long durationSeconds, Long fileSizeBytes);
}
