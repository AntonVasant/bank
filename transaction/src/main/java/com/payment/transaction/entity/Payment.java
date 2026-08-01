package com.payment.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payments")
public class Payment {

    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true)
    private String paymentReference;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    /**
     * Set for intra-bank payments (destination is one of our accounts).
     * Null for inter-bank payments — use destinationBankCode + destinationExternalAccount instead.
     */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_account_id", nullable = true)
    private Account destinationAccount;

    /** Bank.code of the receiving bank — populated only for inter-bank payments. */
    @Setter
    @Column(name = "destination_bank_code", length = 32)
    private String destinationBankCode;

    /** Account number at the external bank — populated only for inter-bank payments. */
    @Setter
    @Column(name = "destination_external_account", length = 64)
    private String destinationExternalAccount;

    /**
     * The rail's settlement cycle id this transaction was recorded against —
     * populated only for inter-bank payments, once the rail's first
     * ({@code RAIL}) acknowledgement comes back. Lets this bank later
     * reconcile its own transaction count/net amount for a cycle against the
     * rail's reported numbers, using the same cycle id.
     */
    @Setter
    @Column(name = "cycle_id", length = 64)
    private String cycleId;

    /**
     * The ledger transaction id created when the hold was placed at
     * {@code prepareInterBankPayment} time (see {@code LedgerTransaction}).
     * Referenced later to either post it (permanent debit, on success) or
     * void it (release the hold, on failure) - see {@code AccountLedgerService}.
     */
    @Setter
    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private PaymentBatch batch;

    @Setter
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Setter
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PaymentStatus {
        PENDING, PROCESSING, CB_ACKNOWLEDGED, COMPLETED, FAILED, REVERSED
    }

}
