package com.payment.transaction.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * This bank's own local record of a credit instructed by the central bank
 * (rail) on behalf of another bank — the {@code RequestCredit} gRPC call
 * handled by {@code CentralBankGatewayGrpcServer}. Kept separate from
 * {@link Payment} (which only represents payments this bank itself
 * initiated) so inter-bank reconciliation can count both directions of
 * traffic for a settlement cycle.
 */
@Getter
@Setter
@Entity
@Table(name = "incoming_credits")
public class IncomingCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_request_id", nullable = false, unique = true, length = 64)
    private String creditRequestId;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    /** The rail's settlement cycle id this credit was recorded against. */
    @Column(name = "cycle_id", length = 64)
    private String cycleId;

    @Column(name = "sender_bank_code", nullable = false, length = 32)
    private String senderBankCode;

    @Column(name = "beneficiary_account_number", nullable = false, length = 64)
    private String beneficiaryAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    /** This receiver bank's own recoverable state machine for this credit — see {@link CreditStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CreditStatus status;

    /** Set when {@link #status} transitions to {@link CreditStatus#FAILED}. */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    void onCreate() {
        receivedAt = LocalDateTime.now();
        if (status == null) {
            status = CreditStatus.RECEIVED;
        }
    }
}
