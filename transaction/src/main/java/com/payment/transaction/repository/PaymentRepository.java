package com.payment.transaction.repository;

import com.payment.transaction.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    /** Lookup by the sender-generated UETR — the same id the rail and the receiver bank track this payment by. */
    Optional<Payment> findByUetr(String uetr);

    List<Payment> findByBatch_Id(Long batchId);

    /** Inter-bank payments (destinationBankCode populated) tagged with the given settlement cycle id. */
    List<Payment> findAllByCycleIdAndDestinationBankCodeIsNotNull(String cycleId);
}
