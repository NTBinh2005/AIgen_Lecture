package com.example.demo.service.serviceImpl;

import com.example.demo.service.LectureAccessGrantVerifier;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Fail-closed adapter until Backend 2 provides the enrollment/assignment API adapter. */
@Component
public class DenyAllLectureAccessGrantVerifier implements LectureAccessGrantVerifier {
    @Override
    public boolean canReadPublishedLecture(Integer studentId, Long lectureId, UUID publishedVersionId) {
        return false;
    }
}
