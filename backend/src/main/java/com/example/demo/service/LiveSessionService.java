package com.example.demo.service;

import com.example.demo.dto.request.LiveSessionCancelRequest;
import com.example.demo.dto.request.LiveSessionCreateRequest;
import com.example.demo.dto.request.LiveSessionUpdateRequest;
import com.example.demo.dto.response.JoinTokenResponse;
import com.example.demo.dto.response.LiveSessionResponse;
import java.util.List;

/**
 * LIVE-01, LIVE-02, LIVE-08.
 */
public interface LiveSessionService {

    LiveSessionResponse create(LiveSessionCreateRequest request, Integer currentUserId);

    LiveSessionResponse findById(Long sessionId, Integer currentUserId);

    List<LiveSessionResponse> findByClass(Integer classId, Integer currentUserId);

    List<LiveSessionResponse> findMine(Integer currentUserId);

    LiveSessionResponse update(Long sessionId, LiveSessionUpdateRequest request, Integer currentUserId);

    LiveSessionResponse cancel(Long sessionId, LiveSessionCancelRequest request, Integer currentUserId);

    /** SCHEDULED → OPEN */
    LiveSessionResponse open(Long sessionId, Integer currentUserId);

    /** OPEN → LIVE */
    LiveSessionResponse goLive(Long sessionId, Integer currentUserId);

    /** LIVE → ENDED */
    LiveSessionResponse end(Long sessionId, Integer currentUserId);

    /** LIVE-02: Cấp join token ngắn hạn sau khi kiểm tra quyền. */
    JoinTokenResponse joinSession(Long sessionId, Integer currentUserId);
}
