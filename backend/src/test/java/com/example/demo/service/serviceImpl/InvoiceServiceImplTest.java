package com.example.demo.service.serviceImpl;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentMethod;
import com.example.demo.entity.SubscriptionPlan;
import com.example.demo.entity.User;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceServiceImplTest {

    @Test
    void generatesVietnamesePdfWithCustomerPlanAndPaymentDetails() throws Exception {
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        InvoiceServiceImpl service = new InvoiceServiceImpl(invoices, mock(UserRepository.class));

        User user = new User();
        user.setUserId(5);
        user.setName("Nguyễn Minh Anh");
        user.setEmail("minhanh@example.com");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("Học viên Nâng cao");

        Payment payment = new Payment();
        payment.setPaymentId(11);
        payment.setUser(user);
        payment.setPlan(plan);
        payment.setTransactionId("DH87654321");
        payment.setPaymentMethod(PaymentMethod.SEPAY);
        payment.setAmount(new BigDecimal("150000"));

        Invoice invoice = new Invoice();
        invoice.setId(23);
        invoice.setInvoiceNumber("INV-1234ABCD");
        invoice.setUser(user);
        invoice.setPayment(payment);
        invoice.setAmount(payment.getAmount());
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.parse("2026-10-15T12:00:00Z"));
        when(invoices.findById(23)).thenReturn(Optional.of(invoice));

        byte[] pdf = service.getInvoicePdfForDelivery(23);

        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            assertThat(new PDFTextStripper().getText(document))
                    .contains("HÓA ĐƠN THANH TOÁN", "Nguyễn Minh Anh", "Học viên Nâng cao", "DH87654321");
        }
    }
}
