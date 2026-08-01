package com.settlement.rail.bank;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The central bank's own clearing/suspense reserve account — the ledger
 * counterparty for every {@code Settlement} row's net-amount posting (see
 * {@code SettlementServiceImpl#applyOne}), so each bank's net settlement
 * movement is recorded as a real double-entry line instead of a bare
 * balance mutation:
 * <pre>
 * Bank's net amount negative (owes):  Debit BankReserve   / Credit CentralReserveAccount
 * Bank's net amount positive (owed):  Debit CentralReserveAccount / Credit BankReserve
 * </pre>
 * A singleton row (fixed id {@link #CENTRAL_RESERVE_ACCOUNT_ID}) — there is
 * only ever one, shared across every bank/currency. Its balance is a useful
 * invariant/reconciliation check: since every cycle's net amounts across
 * banks always sum to zero (one bank's debit is always another's credit),
 * this account's balance should return to its prior value after every
 * fully-applied cycle.
 */
@Entity
@Table(name = "central_reserve_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CentralReserveAccount {

    /** Fixed id of the single central reserve account row. */
    public static final Long CENTRAL_RESERVE_ACCOUNT_ID = 1L;

    @Id
    private Long id;

    @Column(name = "balance", nullable = false, precision = 20, scale = 6)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
