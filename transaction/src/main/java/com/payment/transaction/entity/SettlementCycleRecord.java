package com.payment.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * This bank's own local record of a settlement cycle net position reported
 * by the rail (central bank) via the {@code SettlementReportService} gRPC
 * endpoint.
 */
@Getter
@Setter
@Entity
@Table(
        name = "settlement_cycle_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_settlement_cycle_record_cycle_currency",
                columnNames = {"cycle_id", "currency"}
        )
)
public class SettlementCycleRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business identifier of the settlement cycle, as assigned by the rail. */
    @Column(name = "cycle_id", nullable = false, length = 64)
    private String cycleId;

    @Column(name = "bank_code", nullable = false, length = 32)
    private String bankCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Net settlement amount for this bank/currency for the cycle. */
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    /** Number of transfers this bank was involved in during the cycle. */
    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    /** When the rail generated this net amount. */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.receivedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
