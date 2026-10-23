package com.example.demo.event;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentMethod;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.SubscriptionPlan;
import com.example.demo.entity.User;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.InvoiceService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceEmailFlowTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock InvoiceService invoiceService;
    @Mock EmailService emailService;

    private InvoiceEventListener listener;
    private User user;
    private Payment payment;

    @BeforeEach
    void setUp() {
        listener = new InvoiceEventListener(
                invoiceRepository, paymentRepository, userRepository, invoiceService, emailService);

        user = new User();
        user.setUserId(7);
        user.setName("Nguyễn An");
        user.setEmail("an@example.com");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanId(3);
        plan.setName("Giáo viên Pro");

        payment = new Payment();
        payment.setPaymentId(19);
        payment.setUser(user);
        payment.setPlan(plan);
        payment.setTransactionId("DH12345678");
        payment.setAmount(new BigDecimal("199000"));
        payment.setPaymentMethod(PaymentMethod.SEPAY);
        payment.setStatus(PaymentStatus.SUCCESS);
    }

    @Test
    void createsInvoiceAndEmailsPdfForConfirmedSubscriptionPayment() {
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(invoiceRepository.existsByPaymentPaymentId(19)).thenReturn(false);
        when(paymentRepository.findById(19)).thenReturn(Optional.of(payment));
        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(31);
            return invoice;
        });
        when(invoiceService.getInvoicePdfForDelivery(31)).thenReturn(pdf);

        listener.handlePaymentConfirmedEvent(event());

        verify(invoiceRepository).saveAndFlush(any(Invoice.class));
        verify(emailService).sendEmailWithAttachment(
                eq("an@example.com"),
                anyString(),
                org.mockito.ArgumentMatchers.contains("DH12345678"),
                org.mockito.ArgumentMatchers.matches("INV-[A-F0-9]{8}\\.pdf"),
                eq(pdf),
                eq("application/pdf"));
    }

    @Test
    void duplicateEventDoesNotCreateOrEmailAnotherInvoice() {
        when(invoiceRepository.existsByPaymentPaymentId(19)).thenReturn(true);

        listener.handlePaymentConfirmedEvent(event());

        verify(invoiceRepository, never()).saveAndFlush(any());
        verify(emailService, never()).sendEmailWithAttachment(
                anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void confirmedNonSubscriptionPaymentDoesNotCreateInvoice() {
        payment.setPlan(null);
        when(invoiceRepository.existsByPaymentPaymentId(19)).thenReturn(false);
        when(paymentRepository.findById(19)).thenReturn(Optional.of(payment));
        when(userRepository.findById(7)).thenReturn(Optional.of(user));

        listener.handlePaymentConfirmedEvent(event());

        verify(invoiceRepository, never()).saveAndFlush(any());
        verify(emailService, never()).sendEmailWithAttachment(
                anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    private PaymentConfirmedEvent event() {
        return new PaymentConfirmedEvent(
                payment.getPaymentId(), payment.getTransactionId(), user.getUserId(),
                payment.getPlan() == null ? null : payment.getPlan().getPlanId(), payment.getAmount());
    }
}
