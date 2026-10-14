package com.example.demo.controller.admin;

import com.example.demo.service.MigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Migration", description = "Admin Migration APIs")
@RestController
@RequestMapping("/api/admin/quiz-migrations")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @Operation(summary = "Import old AiElements to Quiz module", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/import-ai-elements/{lectureId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> importAiElements(@PathVariable Long lectureId) {
        migrationService.migrateAiElementsToQuiz(lectureId);
        return ResponseEntity.ok().build();
    }
}
