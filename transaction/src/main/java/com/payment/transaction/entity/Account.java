package com.payment.transaction.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Funds locked by an in-flight interbank ledger hold (placed at request
     * time, before the interbank round trip confirms or fails). Available
     * balance for new payments is balance minus heldBalance. On success the
     * hold is posted (heldBalance decreases, balance decreases by the same
     * amount); on failure it is voided (heldBalance decreases, balance
     * untouched) - see AccountLedgerService.
     */
    @Column(name = "held_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal heldBalance;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sourceAccount", fetch = FetchType.LAZY)
    private List<Payment> outgoingPayments;

    @OneToMany(mappedBy = "destinationAccount", fetch = FetchType.LAZY)
    private List<Payment> incomingPayments;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (balance == null) balance = BigDecimal.ZERO;
        if (heldBalance == null) heldBalance = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccountStatus {
        ACTIVE, INACTIVE, FROZEN, CLOSED
    }

    public enum Currency {
        USD, EUR, GBP, NGN
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getHeldBalance() { return heldBalance; }
    public void setHeldBalance(BigDecimal heldBalance) { this.heldBalance = heldBalance; }

    /** Funds actually available for new holds/payments: balance minus heldBalance. */
    public BigDecimal getAvailableBalance() {
        return balance.subtract(heldBalance == null ? BigDecimal.ZERO : heldBalance);
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<Payment> getOutgoingPayments() { return outgoingPayments; }
    public List<Payment> getIncomingPayments() { return incomingPayments; }
}
