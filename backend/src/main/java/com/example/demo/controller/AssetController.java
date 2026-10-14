package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.response.AssetResponse;
import com.example.demo.entity.AssetPurpose;
import com.example.demo.service.AssetContent;
import com.example.demo.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Assets", description = "Backend 3 binary asset storage")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {
    private final AssetService assetService;

    @Operation(summary = "Upload an asset", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssetResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam AssetPurpose purpose,
            @RequestParam(required = false) String externalSourceUrl,
            @RequestParam(required = false) String licenseStatus,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserPrincipal authenticated = requirePrincipal(principal);
        requireTeacherOrAdmin(authenticated);
        AssetResponse response = assetService.upload(
                authenticated.getUserId(), purpose, file, externalSourceUrl, licenseStatus);
        return ResponseEntity
                .created(URI.create("/api/assets/" + response.assetId()))
                .body(response);
    }

    @Operation(summary = "Get asset metadata", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{assetId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AssetResponse> getMetadata(
            @PathVariable UUID assetId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserPrincipal authenticated = requirePrincipal(principal);
        return ResponseEntity.ok(assetService.getMetadata(
                assetId, authenticated.getUserId(), isAdmin(authenticated)));
    }

    @Operation(summary = "Download asset content", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{assetId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID assetId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserPrincipal authenticated = requirePrincipal(principal);
        AssetContent content = assetService.getContent(
                assetId, authenticated.getUserId(), isAdmin(authenticated));
        byte[] bytes = content.getBytes();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }

    @Operation(summary = "Archive an asset", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{assetId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> archive(
            @PathVariable UUID assetId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserPrincipal authenticated = requirePrincipal(principal);
        assetService.archive(assetId, authenticated.getUserId(), isAdmin(authenticated));
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal requirePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return principal;
    }

    private void requireTeacherOrAdmin(UserPrincipal principal) {
        boolean allowed = principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TEACHER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
        if (!allowed) {
            throw new AccessDeniedException("Only teachers and administrators may upload assets");
        }
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
