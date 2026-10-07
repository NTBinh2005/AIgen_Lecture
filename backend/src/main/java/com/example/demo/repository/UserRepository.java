package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByEmail(String email);

    // FIX #23: Giáo viên tìm học sinh theo email (khớp một phần, không phân biệt hoa thường).
    List<User> findTop20ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(UserRole role, String email);

    boolean existsByEmailAndUserIdNot(String email, Integer userId);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByGoogleSubject(String googleSubject);

    long countByRole(UserRole role);
}
