package com.example.demo.repository;

import com.example.demo.entity.ClassPresentation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassPresentationRepository extends JpaRepository<ClassPresentation, Long> {
    List<ClassPresentation> findByClassEntity_ClassIdOrderByAssignedAtDesc(Integer classId);

    Optional<ClassPresentation> findByClassEntity_ClassIdAndPresentation_PresentationId(
            Integer classId, UUID presentationId);

    /** Học sinh xem được bản trình chiếu nếu nó được giao cho lớp mà học sinh đang ghi danh ACTIVE. */
    @Query("SELECT CASE WHEN COUNT(cp) > 0 THEN true ELSE false END "
            + "FROM ClassPresentation cp, ClassStudent cs "
            + "WHERE cp.presentation.presentationId = :presentationId "
            + "AND cs.classEntity.classId = cp.classEntity.classId "
            + "AND cs.student.userId = :studentId "
            + "AND cs.status = com.example.demo.entity.EnrollmentStatus.ACTIVE")
    boolean existsActiveEnrollmentGrant(
            @Param("studentId") Integer studentId, @Param("presentationId") UUID presentationId);
}
