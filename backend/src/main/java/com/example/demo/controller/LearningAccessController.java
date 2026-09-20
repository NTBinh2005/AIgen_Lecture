package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.response.LearningAccessResponse;
import com.example.demo.service.LearningAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Learning Access", description = "API for querying learning access / content permissions")
@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class LearningAccessController {

    private final LearningAccessService learningAccessService;

    @Operation(summary = "Get all learning accesses of the current user",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my-access")
    public ResponseEntity<List<LearningAccessResponse>> getMyAccess(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(learningAccessService.getMyAccess(principal.getUserId()));
    }

    @Operation(summary = "Check if the current user has access to a specific product/course",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Integer productId) {
        boolean allowed = learningAccessService.hasAccess(principal.getUserId(), productId);
        return ResponseEntity.ok(Map.of("hasAccess", allowed));
    }

    @Operation(summary = "Internal inter-service check: verify if a specific user has access to a course/product")
    @GetMapping("/check-user")
    public ResponseEntity<Map<String, Object>> checkUserAccess(
            @RequestParam Integer userId,
            @RequestParam Integer productId) {
        boolean allowed = learningAccessService.hasAccess(userId, productId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "productId", productId,
                "hasAccess", allowed
        ));
    }
}
