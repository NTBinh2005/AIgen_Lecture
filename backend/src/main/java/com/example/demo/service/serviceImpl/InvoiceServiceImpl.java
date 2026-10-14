package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.InvoiceResponse;
import com.example.demo.entity.Invoice;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.service.InvoiceService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        
        // Dummy PDF generation logic - return string bytes
        String text = "Invoice PDF Content\n" +
                "Invoice ID: " + invoice.id() + "\n" +
                "Invoice Number: " + invoice.invoiceNumber() + "\n" +
                "Amount: " + invoice.amount();
        return text.getBytes(StandardCharsets.UTF_8);
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
}
