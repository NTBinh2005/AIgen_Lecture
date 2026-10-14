package com.example.demo.service;

import com.example.demo.dto.response.InvoiceResponse;
import java.util.List;

public interface InvoiceService {
    List<InvoiceResponse> getMyInvoices(Integer userId);
    InvoiceResponse getInvoiceById(Integer userId, Integer invoiceId);
    byte[] getInvoicePdf(Integer userId, Integer invoiceId);
}
