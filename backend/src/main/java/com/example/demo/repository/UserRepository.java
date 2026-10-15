package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role = com.example.demo.entity.UserRole.ADMIN and u.status = com.example.demo.entity.UserStatus.ACTIVE order by u.userId")
    List<User> lockActiveAdmins();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :id")
    Optional<User> lockByUserId(@org.springframework.data.repository.query.Param("id") Integer id);
    @org.springframework.data.jpa.repository.Query("select year(u.createdAt), month(u.createdAt), u.role, count(u) from User u where u.createdAt >= :since group by year(u.createdAt), month(u.createdAt), u.role")
    List<Object[]> monthlyGrowth(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    // FIX #23: Giáo viên tìm học sinh theo email (khớp một phần, không phân biệt hoa thường).
    List<User> findTop20ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(UserRole role, String email);

    boolean existsByEmailAndUserIdNot(String email, Integer userId);
    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, Integer userId);

    Optional<User> findByEmail(String email);
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(trim(u.email)) = lower(:email) and u.emailAmbiguous = false")
    Optional<User> findByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByGoogleSubject(String googleSubject);

    long countByRole(UserRole role);
    List<User> findByRoleAndStatus(UserRole role, com.example.demo.entity.UserStatus status);

    long countByRoleAndStatus(UserRole role, com.example.demo.entity.UserStatus status);
}
