package com.example.demo.repository;

import com.example.demo.entity.SessionRecording;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRecordingRepository extends JpaRepository<SessionRecording, Long> {

    Optional<SessionRecording> findBySession_SessionId(Long sessionId);

    Optional<SessionRecording> findByExternalRecordingId(String externalRecordingId);
}
