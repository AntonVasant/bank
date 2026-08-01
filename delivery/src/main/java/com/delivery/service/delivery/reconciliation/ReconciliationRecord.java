package com.delivery.service.delivery.reconciliation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * This service's own durable record of a bank's reconciliation result,
 * persisted the moment {@link ReconciliationResultController} receives it —
 * before (and independently of) relaying it onward to the rail via the
 * outbox/Kafka, so delivery keeps its own audit trail of what every bank
 * reported rather than only ever forwarding it.
 */
@Entity
@Table(
        name = "reconciliation_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reconciliation_record_cycle_bank_currency",
                columnNames = {"cycle_id", "bank_code", "currency"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRecord {

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

    /** When the bank performed its own comparison. */
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    /** When delivery received and recorded this result. */
    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
}
