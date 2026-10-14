package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.response.InvoiceResponse;
import com.example.demo.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Invoice", description = "API for handling invoices")
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "Get all invoices of current user", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getMyInvoices(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(invoiceService.getMyInvoices(principal.getUserId()));
    }

    @Operation(summary = "Get an invoice by ID", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(principal.getUserId(), id));
    }

    @Operation(summary = "Get an invoice as PDF", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getInvoicePdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        byte[] pdfBytes = invoiceService.getInvoicePdf(principal.getUserId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice_" + id + ".txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(pdfBytes);
    }
}
