package com.payment.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * This bank's own settlement (clearing) account — the counterparty to every
 * customer account in an interbank payment: debited when this bank pays
 * another bank on a customer's behalf, credited when another bank pays one
 * of this bank's customers. A singleton row (fixed id {@link #SETTLEMENT_ACCOUNT_ID}),
 * since each bank instance owns exactly one. Replaces the TigerBeetle
 * Interbank Payable/Receivable accounts — its balance is now managed
 * directly here, in Postgres, by {@code AccountLedgerService}.
 */
@Entity
@Table(name = "settlement_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementAccount {

    /** Fixed id of this bank instance's single settlement account row. */
    public static final Long SETTLEMENT_ACCOUNT_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
