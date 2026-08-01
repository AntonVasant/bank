package com.settlement.rail.settlement;

import com.settlement.rail.bank.Bank;
import com.settlement.rail.cycle.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single bank's settlement result for a settlement cycle \u2014 the persisted
 * business state that the Settlement batch job's three steps read and write.
 * Spring Batch itself only orchestrates; nothing here lives in a
 * {@code JobExecution}/{@code StepExecution} {@code ExecutionContext}.
 * <p>
 * Pipeline (each stage independently restartable by re-reading this table):
 * <ol>
 *   <li>Calculate \u2014 one row created per bank/currency, {@code status = PENDING}.</li>
 *   <li>Apply \u2014 reserve balance updated, row flips to {@code status = APPLIED}.</li>
 *   <li>Create Outbox \u2014 one outbox event queued, {@code outboxCreated = true}.</li>
 * </ol>
 */
@Entity
@Table(
        name = "settlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_settlement_cycle_bank_currency",
                columnNames = {"cycle_id", "bank_id", "currency"}
        ),
        indexes = {
                @Index(name = "idx_settlement_cycle_id", columnList = "cycle_id"),
                @Index(name = "idx_settlement_status", columnList = "status"),
                @Index(name = "idx_settlement_outbox_created", columnList = "outbox_created")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier of the settlement cycle this row was calculated for (the SettlementCycle's own id). */
    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_credit", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalCredit;

    @Column(name = "total_debit", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalDebit;

    /** totalCredit - totalDebit for this bank/currency/cycle. */
    @Column(name = "net_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal netAmount;

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private int transactionCount = 0;

    /** Deterministic integrity check over this row's key values. */
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    /** Step 1 -> Step 2 pipeline state. Nullable at the schema level for safe rollout. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    /** Step 3 completion flag \u2014 becomes true once the outbox event has been queued. */
    @Column(name = "outbox_created", nullable = false)
    @Builder.Default
    private boolean outboxCreated = false;

    /**
     * Whether this settlement has been published for reconciliation with the
     * owning bank yet. Independent of {@link #status}/{@link #outboxCreated}.
     * Nullable at the schema level for safe rollout.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 20)
    @Builder.Default
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "outbox_created_at")
    private Instant outboxCreatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        if (this.totalCredit == null) {
            this.totalCredit = BigDecimal.ZERO;
        }
        if (this.totalDebit == null) {
            this.totalDebit = BigDecimal.ZERO;
        }
        if (this.netAmount == null) {
            this.netAmount = this.totalCredit.subtract(this.totalDebit);
        }
        if (this.status == null) {
            this.status = SettlementStatus.PENDING;
        }
        if (this.reconciliationStatus == null) {
            this.reconciliationStatus = ReconciliationStatus.PENDING;
        }
    }
}
