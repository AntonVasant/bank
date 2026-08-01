package com.settlement.rail.bank;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "bank_reserves",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bank_reserves_bank_currency",
                columnNames = {"bank_id", "currency"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankReserve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Total reserve balance this bank holds in the settlement rail.
     */
    @Column(name = "balance", nullable = false, precision = 20, scale = 6)
    private BigDecimal balance;

    /**
     * Portion of the balance locked for pending outgoing transfers.
     */
    @Column(name = "reserved_balance", nullable = false, precision = 20, scale = 6)
    private BigDecimal reservedBalance;


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
        if (this.reservedBalance == null) {
            this.reservedBalance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Available balance = balance - reservedBalance */
    @Transient
    public BigDecimal getAvailableBalance() {
        return this.balance.subtract(this.reservedBalance);
    }
}
