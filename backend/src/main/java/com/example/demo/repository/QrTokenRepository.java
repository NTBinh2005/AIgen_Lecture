package com.example.demo.repository;

import com.example.demo.entity.QrToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrTokenRepository extends JpaRepository<QrToken, Long> {

    Optional<QrToken> findByCode(String code);
}
