package com.example.demo.service.serviceImpl;

import com.example.demo.repository.ClassLectureRepository;
import com.example.demo.service.LectureAccessGrantVerifier;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FIX #1: Xác thực quyền xem bài giảng đã publish dựa trên enrollment thật.
 *
 * <p>Học sinh được đọc một bài giảng đã publish nếu bài đó được giao cho một lớp
 * mà học sinh đang có enrollment {@code ACTIVE}. Thay cho adapter fail-closed
 * trước đây (luôn trả {@code false}).
 */
@Component
@RequiredArgsConstructor
public class EnrollmentLectureAccessGrantVerifier implements LectureAccessGrantVerifier {

    private final ClassLectureRepository classLectureRepository;

    @Override
    public boolean canReadPublishedLecture(Integer studentId, Long lectureId, UUID publishedVersionId) {
        if (studentId == null || lectureId == null) {
            return false;
        }
        return classLectureRepository.existsActiveEnrollmentGrant(studentId, lectureId);
    }
}
