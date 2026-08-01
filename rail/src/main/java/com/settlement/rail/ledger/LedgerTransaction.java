package com.settlement.rail.ledger;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Replaces TigerBeetle as the immutable, double-entry ledger of record for
 * settled interbank payments. One row per {@link com.settlement.rail.payment.Payment}
 * posted to the ledger \u2014 see {@link #paymentId}. Its {@link LedgerEntry}
 * children (always exactly two \u2014 one DEBIT, one CREDIT) carry the actual
 * bank reserve account movements.
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

    /** The {@code Payment.id} this ledger transaction was posted for. */
    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerTransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
