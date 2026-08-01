package com.settlement.rail.transfer;

import com.settlement.rail.bank.Bank;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utr", nullable = false, unique = true, length = 64)
    private String utr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_bank_id", nullable = false)
    private Bank fromBank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_bank_id", nullable = false)
    private Bank toBank;

    @Column(name = "amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    /**
     * This rail's own recoverable state for its interaction with the
     * <b>receiver</b> bank — see {@link CreditLegStatus}. Independent of
     * {@link #status} (the overall/debit-facing lifecycle), since the two
     * legs (accepting from the sender, crediting the receiver) can each
     * fail or crash independently.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "credit_status", nullable = false)
    private CreditLegStatus creditStatus;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sender_account_number", length = 64)
    private String senderAccountNumber;

    @Column(name = "beneficiary_account_number", length = 64)
    private String beneficiaryAccountNumber;

    /**
     * The settlement cycle this transfer was recorded against. Sent back to
     * both banks via gRPC (PaymentResponse.cycle_id / CreditRequest.cycle_id)
     * so each bank can tag its own local record with the same cycle id and
     * later reconcile against the rail's per-cycle net position.
     */
    @Column(name = "cycle_id", length = 64)
    private String cycleId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) {
            this.status = TransferStatus.PENDING;
        }
        if (this.creditStatus == null) {
            this.creditStatus = CreditLegStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
