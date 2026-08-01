package com.payment.transaction.ledger;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Replaces TigerBeetle as the immutable, double-entry ledger of record for
 * this bank's settlement-account-involving payments (interbank holds,
 * posts and incoming credits). One row per payment/incoming-credit event —
 * see {@link #paymentId}. Its {@link LedgerEntryLine} children (always exactly
 * two — one DEBIT, one CREDIT) carry the actual account movements.
 */
@Entity
@Table(name = "ledger_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    /**
     * The id of the local payment-side record this ledger transaction was
     * created for — {@code Payment.id} for a hold placed on the sending
     * side, or {@code IncomingCredit.id} for a credit applied on the
     * receiving side. Never both for the same row, so this stays unique.
     */
    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerTransactionStatus status;

    /**
     * The settlement cycle this transaction is tagged with — the confirmed
     * cycle id from the rail once known (updated at {@code postHold} time),
     * or the provisional cached one at hold time otherwise. Lets this bank
     * independently recompute its own net position per cycle for
     * reconciliation against the rail's reported numbers (see
     * {@code ReconciliationService}), without needing a full DB migration
     * of every ledger row when a cycle-mismatch retry corrects it.
     */
    @Column(name = "cycle_id")
    private Long cycleId;

    /** The customer account's currency at the time this transaction was created — needed to scope reconciliation per bank/currency/cycle, matching the rail's own {@code Settlement} dimensions. */
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
