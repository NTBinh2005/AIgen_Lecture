package com.example.demo.repository;

import com.example.demo.entity.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {
    List<Invoice> findByUserUserId(Integer userId);
    boolean existsByPaymentPaymentId(Integer paymentId);
}
