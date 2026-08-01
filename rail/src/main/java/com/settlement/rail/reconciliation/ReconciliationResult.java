package com.settlement.rail.reconciliation;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The outcome of a bank independently recomputing its own transaction count
 * and net amount for a settlement cycle and comparing it against the rail's
 * reported numbers. Reported back to the rail by the notification service
 * once the bank has performed the check.
 */
@Entity
@Table(
        name = "reconciliation_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reconciliation_result_cycle_bank_currency",
                columnNames = {"cycle_id", "bank_code", "currency"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false, length = 64)
    private String cycleId;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "rail_transaction_count", nullable = false)
    private int railTransactionCount;

    @Column(name = "rail_net_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal railNetAmount;

    @Column(name = "bank_transaction_count", nullable = false)
    private int bankTransactionCount;

    @Column(name = "bank_net_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal bankNetAmount;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    /** When the bank performed the comparison. */
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    /** When the rail recorded this result. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void prePersist() {
        this.receivedAt = Instant.now();
    }
}
