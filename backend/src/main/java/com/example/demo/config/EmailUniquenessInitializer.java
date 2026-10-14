package com.example.demo.config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
@Component @RequiredArgsConstructor
public class EmailUniquenessInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments args) throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        }
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_normalized_email ON users (lower(trim(email))) WHERE email_ambiguous = FALSE");
    }
}
