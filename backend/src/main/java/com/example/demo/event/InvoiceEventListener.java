package com.example.demo.event;

import com.example.demo.entity.Invoice;
import com.example.demo.entity.InvoiceStatus;
import com.example.demo.entity.Payment;
import com.example.demo.entity.User;
import com.example.demo.repository.InvoiceRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InvoiceEventListener {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handlePaymentConfirmedEvent(PaymentConfirmedEvent event) {
        if (invoiceRepository.existsByPaymentPaymentId(event.paymentId())) {
            return; // Avoid duplicate invoices
        }

        Payment payment = paymentRepository.findById(event.paymentId()).orElse(null);
        User user = userRepository.findById(event.userId()).orElse(null);

        if (payment != null && user != null) {
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            invoice.setUser(user);
            invoice.setPayment(payment);
            invoice.setAmount(event.amount());
            invoice.setStatus(InvoiceStatus.ISSUED);
            invoice.setIssuedAt(event.timestamp());
            invoiceRepository.save(invoice);
        }
    }
}
