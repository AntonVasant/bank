package com.settlement.rail.payment;

import com.settlement.rail.bank.Bank;
import com.settlement.rail.transfer.Transfer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The real-time, per-transaction financial record of a single interbank
 * transfer — one row created the moment a transfer is accepted (in the same
 * transaction as {@link Transfer}). This is the sole real-time source of
 * truth for both settlement calculation (net position per bank/currency for
 * a cycle is aggregated directly from these rows — see
 * {@code PaymentRepository#aggregateDebitsByBankForCycle}/
 * {@code aggregateCreditsByBankForCycle}) and ledger posting.
 * <p>
 * This is the input the settlement job posts to the Postgres ledger once its
 * settlement cycle closes: every {@code Payment} becomes exactly one
 * {@code LedgerTransaction}, debiting {@link #fromBank}'s reserve account and
 * crediting {@link #toBank}'s reserve account, making the ledger the
 * immutable financial audit trail while this table remains the
 * real-time, restart-safe queue of work still to be posted there.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_cycle_id", columnList = "cycle_id"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_transfer_id", columnList = "transfer_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Column(name = "utr", nullable = false, length = 64)
    private String utr;

    /**
     * The settlement cycle this payment was recorded against \u2014 the OPEN
     * cycle at the moment the transfer was accepted. Once that cycle
     * closes, the settlement job posts every payment tagged with it to
     * the ledger. Plain {@code Long} (not a relation), same convention as
     * {@code Settlement.cycleId}.
     */
    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    /** The bank debited \u2014 the sender / originating bank. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_bank_id", nullable = false)
    private Bank fromBank;

    /** The bank credited \u2014 the beneficiary bank. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_bank_id", nullable = false)
    private Bank toBank;

    /** Amount debited from {@link #fromBank} and credited to {@link #toBank}. */
    @Column(name = "amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentLedgerStatus status = PaymentLedgerStatus.PENDING;

    /** The ledger transaction id this payment was posted as, once {@code status = POSTED}. */
    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = PaymentLedgerStatus.PENDING;
        }
    }
}
