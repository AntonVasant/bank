package com.payment.transaction.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single line in the double-entry ledger.
 *
 * Every {@link Payment} generates exactly two LedgerEntry rows:
 *   - DEBIT  on the source account      (money leaving)
 *   - CREDIT on the destination account (money arriving)
 *
 * The invariant that must always hold is:
 *   SUM(DEBIT entries) == SUM(CREDIT entries)  for a given payment reference.
 */
@Entity
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_payment_id",   columnList = "payment_id"),
        @Index(name = "idx_ledger_account_id",   columnList = "account_id"),
        @Index(name = "idx_ledger_entry_ref",    columnList = "entry_reference", unique = true),
        @Index(name = "idx_ledger_posted_at",    columnList = "posted_at")
    }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique, human-readable reference for this ledger line (e.g. "PAY-123-DEBIT").
     * Useful for reconciliation and idempotency checks.
     */
    @Column(name = "entry_reference", nullable = false, unique = true, length = 64)
    private String entryReference;

    /** The payment that originated these ledger entries. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /**
     * The account affected by this entry.
     * DEBIT  → always our source account.
     * CREDIT → our destination account (intra-bank) or null (inter-bank, settled externally).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "account_id", nullable = true)
    private Account account;

    /** Account number of the sending side — always populated, intra-bank or inter-bank. */
    @Column(name = "source_account_number", nullable = false, length = 64)
    private String sourceAccountNumber;

    /** Account number of the receiving side — always populated, intra-bank or inter-bank. */
    @Column(name = "destination_account_number", nullable = false, length = 64)
    private String destinationAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Account.Currency currency;

    /** Free-text description, e.g. "Payment to ACCT-0042 for invoice #99". */
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Set once this entry is finalized (posted / settled). */
    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    /** Set once this entry is reversed / cancelled (e.g. on payment reversal). */
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // -----------------------------------------------------------------------
    // Enums
    // -----------------------------------------------------------------------

    public enum EntryType {
        /** Money leaves this account. */
        DEBIT,
        /** Money enters this account. */
        CREDIT
    }

    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public Long getId() { return id; }

    public String getEntryReference() { return entryReference; }
    public void setEntryReference(String entryReference) { this.entryReference = entryReference; }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public String getSourceAccountNumber() { return sourceAccountNumber; }
    public void setSourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; }

    public String getDestinationAccountNumber() { return destinationAccountNumber; }
    public void setDestinationAccountNumber(String destinationAccountNumber) { this.destinationAccountNumber = destinationAccountNumber; }

    public EntryType getEntryType() { return entryType; }
    public void setEntryType(EntryType entryType) { this.entryType = entryType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Account.Currency getCurrency() { return currency; }
    public void setCurrency(Account.Currency currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }

    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
}
