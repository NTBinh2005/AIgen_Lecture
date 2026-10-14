package com.example.demo.event;

import com.example.demo.repository.PaymentRepository;
import com.example.demo.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SubscriptionPaymentEventListener {
    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activate(PaymentConfirmedEvent event) {
        paymentRepository.findById(event.paymentId()).ifPresent(subscriptionService::activateFromPayment);
    }
}
