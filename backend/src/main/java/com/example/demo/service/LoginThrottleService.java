package com.example.demo.service;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DuplicateKeyException;
@Service @RequiredArgsConstructor
public class LoginThrottleService {
    private final JdbcTemplate jdbc;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    public String key(String email, String remoteAddress) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((email + "|" + remoteAddress).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public void assertAllowed(String key) {
        Instant cutoff = Instant.now().minus(WINDOW);
        boolean blocked = jdbc.query("SELECT failure_count, last_failure FROM login_failures WHERE throttle_key = ?",
                (rs, row) -> rs.getInt(1) >= 5 && rs.getTimestamp(2).toInstant().isAfter(cutoff), key)
                .stream().anyMatch(Boolean::booleanValue);
        if (blocked) throw new BadCredentialsException("Quá nhiều lần đăng nhập sai từ địa chỉ này. Vui lòng thử lại sau 5 phút");
    }
    public void failed(String key) {
        Timestamp now = Timestamp.from(Instant.now()), cutoff = Timestamp.from(Instant.now().minus(WINDOW));
        // Each statement commits independently: the fallback insert race never poisons an outer auth transaction.
        String update = "UPDATE login_failures SET failure_count = CASE WHEN last_failure < ? THEN 1 ELSE failure_count + 1 END, last_failure = ? WHERE throttle_key = ?";
        if (jdbc.update(update, cutoff, now, key) == 0) {
            try { jdbc.update("INSERT INTO login_failures(throttle_key, failure_count, last_failure) VALUES (?, 1, ?)", key, now); }
            catch (DuplicateKeyException concurrentInsert) { jdbc.update(update, cutoff, now, key); }
        }
    }
    public void succeeded(String key) { jdbc.update("DELETE FROM login_failures WHERE throttle_key = ?", key); }
    @Scheduled(fixedDelayString="${app.auth.throttle-cleanup-ms:300000}")
    public void cleanup() { jdbc.update("DELETE FROM login_failures WHERE last_failure < ?", Timestamp.from(Instant.now().minus(WINDOW))); }
}
