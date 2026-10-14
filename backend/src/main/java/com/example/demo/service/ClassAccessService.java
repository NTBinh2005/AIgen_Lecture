package com.example.demo.service;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ClassTeacherRepository;
import com.example.demo.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Centralized ownership and enrollment checks for Backend 2 resources. */
@Service
@RequiredArgsConstructor
public class ClassAccessService {

    private final UserRepository userRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassStudentRepository classStudentRepository;

    public User requireUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public boolean canManage(ClassEntity classEntity, Integer userId) {
        User user = requireUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }
        return user.getRole() == UserRole.TEACHER
                && (classEntity.getTeacher().getUserId().equals(userId)
                || classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(
                        classEntity.getClassId(), userId));
    }

    public void assertCanManage(ClassEntity classEntity, Integer userId) {
        if (!canManage(classEntity, userId)) {
            throw new AccessDeniedException("User cannot manage this class");
        }
    }

    public boolean canView(ClassEntity classEntity, Integer userId) {
        User user = requireUser(userId);
        if (user.getRole() == UserRole.ADMIN || canManageWithoutReload(classEntity, user)) {
            return true;
        }
        return user.getRole() == UserRole.STUDENT
                && classStudentRepository
                .findByClassEntity_ClassIdAndStudent_UserIdAndStatusIn(
                        classEntity.getClassId(), userId,
                        List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))
                .isPresent();
    }

    public void assertCanView(ClassEntity classEntity, Integer userId) {
        if (!canView(classEntity, userId)) {
            throw new AccessDeniedException("User cannot view this class");
        }
    }

    public void assertActiveStudent(ClassEntity classEntity, Integer studentId) {
        boolean active = classStudentRepository
                .findByClassEntity_ClassIdAndStudent_UserIdAndStatusIn(
                        classEntity.getClassId(), studentId, List.of(EnrollmentStatus.ACTIVE))
                .isPresent();
        if (!active) {
            throw new AccessDeniedException("Student does not have an ACTIVE enrollment");
        }
    }

    private boolean canManageWithoutReload(ClassEntity classEntity, User user) {
        return user.getRole() == UserRole.TEACHER
                && (classEntity.getTeacher().getUserId().equals(user.getUserId())
                || classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(
                        classEntity.getClassId(), user.getUserId()));
    }
}
