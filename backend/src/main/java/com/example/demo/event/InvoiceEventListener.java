package com.example.demo.event;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.entity.Payment;
import com.example.demo.entity.User;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.InvoiceService;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class InvoiceEventListener {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final EmailService emailService;

    public InvoiceEventListener(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            InvoiceService invoiceService,
            EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handlePaymentConfirmedEvent(PaymentConfirmedEvent event) {
        if (invoiceRepository.existsByPaymentPaymentId(event.paymentId())) {
            return; // Avoid duplicate invoices
        }

        Payment payment = paymentRepository.findById(event.paymentId()).orElse(null);
        User user = userRepository.findById(event.userId()).orElse(null);

        if (payment != null && user != null && payment.getPlan() != null) {
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            invoice.setUser(user);
            invoice.setPayment(payment);
            invoice.setAmount(event.amount());
            invoice.setStatus(InvoiceStatus.ISSUED);
            invoice.setIssuedAt(event.timestamp());
            Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);
            scheduleEmailAfterCommit(savedInvoice, user);
        }
    }

    private void scheduleEmailAfterCommit(Invoice invoice, User user) {
        if (!isDeliverableEmail(user.getEmail())) {
            log.warn("[Invoice] Invoice {} was created but userId={} has no deliverable email",
                    invoice.getInvoiceNumber(), user.getUserId());
            return;
        }

        final byte[] pdf;
        try {
            pdf = invoiceService.getInvoicePdfForDelivery(invoice.getId());
        } catch (RuntimeException ex) {
            log.error("[Invoice] Could not generate PDF for invoice {}", invoice.getInvoiceNumber(), ex);
            return;
        }

        Runnable send = () -> emailService.sendEmailWithAttachment(
                user.getEmail(),
                "Hóa đơn thanh toán EduMind " + invoice.getInvoiceNumber(),
                buildEmailBody(user, invoice),
                invoice.getInvoiceNumber() + ".pdf",
                pdf,
                "application/pdf");

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private String buildEmailBody(User user, Invoice invoice) {
        String planName = invoice.getPayment().getPlan().getName();
        return "Xin chào " + user.getName() + ",\n\n"
                + "EduMind đã nhận thanh toán cho gói " + planName + ".\n"
                + "Mã giao dịch: " + invoice.getPayment().getTransactionId() + "\n"
                + "Số hóa đơn: " + invoice.getInvoiceNumber() + "\n"
                + "Số tiền: " + invoice.getAmount().toPlainString() + " VND\n\n"
                + "Hóa đơn PDF được đính kèm trong email này.\n"
                + "Cảm ơn bạn đã sử dụng EduMind.";
    }

    private boolean isDeliverableEmail(String email) {
        return StringUtils.hasText(email)
                && !email.toLowerCase(java.util.Locale.ROOT).endsWith("@phone.local");
    }
}
