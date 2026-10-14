package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.InvoiceResponse;
import com.example.demo.entity.Invoice;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.service.InvoiceService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getMyInvoices(Integer userId) {
        return invoiceRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Integer userId, Integer invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!invoice.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Access denied to this invoice");
        }

        return toResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getInvoicePdf(Integer userId, Integer invoiceId) {
        InvoiceResponse invoice = getInvoiceById(userId, invoiceId);
        
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 18);
                content.newLineAtOffset(72, 740);
                content.showText("EDUMIND INVOICE");
                content.setLeading(24);
                content.newLine();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.showText("Invoice number: " + safe(invoice.invoiceNumber()));
                content.newLine();
                content.showText("Invoice ID: " + invoice.id());
                content.newLine();
                content.showText("Payment ID: " + invoice.paymentId());
                content.newLine();
                content.showText("Amount: " + invoice.amount());
                content.newLine();
                content.showText("Status: " + invoice.status());
                content.newLine();
                content.showText("Issued at: " + invoice.issuedAt());
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate invoice PDF", exception);
        }
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getUser().getUserId(),
                invoice.getPayment().getPaymentId(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getIssuedAt(),
                invoice.getCreatedAt()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
