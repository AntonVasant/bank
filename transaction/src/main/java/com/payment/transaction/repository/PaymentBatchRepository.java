package com.payment.transaction.repository;

import com.payment.transaction.entity.PaymentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {

    Optional<PaymentBatch> findByBatchReference(String batchReference);
}
