package com.example.demo.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="login_failures") @Getter @Setter
public class LoginFailure {
    @Id @Column(name="throttle_key", length=64) private String throttleKey;
    @Column(name="failure_count", nullable=false) private int failureCount;
    @Column(name="last_failure", nullable=false) private Instant lastFailure;
}
