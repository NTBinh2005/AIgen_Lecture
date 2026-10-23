package com.example.demo.service;

import com.example.demo.dto.response.InvoiceResponse;
import java.util.List;

public interface InvoiceService {
    org.springframework.data.domain.Page<InvoiceResponse> getMyInvoices(
            Integer userId, org.springframework.data.domain.Pageable pageable);
    InvoiceResponse getInvoiceById(Integer userId, Integer invoiceId);
    byte[] getInvoicePdf(Integer userId, Integer invoiceId);
    byte[] getInvoicePdfForDelivery(Integer invoiceId);
}
