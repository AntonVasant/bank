package com.settlement.rail.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByCycleId(Long cycleId);

    /** Payments for this cycle not yet posted to the ledger (restart-safe input for the ledger posting step). */
    List<Payment> findAllByCycleIdAndStatus(Long cycleId, PaymentLedgerStatus status);

    /**
     * Sums the debit side (the {@code fromBank}) of every payment tagged
     * with the given settlement cycle, grouped by bank and currency. Used,
     * together with {@link #aggregateCreditsByBankForCycle}, to compute each
     * bank's net settlement position for that cycle directly from
     * {@link Payment} — the real-time record of every transfer — instead of
     * a separate ledger-entries table.
     */
    @Query("""
            SELECT p.fromBank.id AS bankId, p.fromBank.bankCode AS bankCode, p.currency AS currency,
                   SUM(p.amount) AS totalAmount, COUNT(p.id) AS entryCount
            FROM Payment p
            WHERE p.cycleId = :cycleId
            GROUP BY p.fromBank.id, p.fromBank.bankCode, p.currency
            """)
    List<PaymentBankAggregate> aggregateDebitsByBankForCycle(@Param("cycleId") Long cycleId);

    /**
     * Sums the credit side (the {@code toBank}) of every payment tagged
     * with the given settlement cycle, grouped by bank and currency.
     */
    @Query("""
            SELECT p.toBank.id AS bankId, p.toBank.bankCode AS bankCode, p.currency AS currency,
                   SUM(p.amount) AS totalAmount, COUNT(p.id) AS entryCount
            FROM Payment p
            WHERE p.cycleId = :cycleId
            GROUP BY p.toBank.id, p.toBank.bankCode, p.currency
            """)
    List<PaymentBankAggregate> aggregateCreditsByBankForCycle(@Param("cycleId") Long cycleId);
}

