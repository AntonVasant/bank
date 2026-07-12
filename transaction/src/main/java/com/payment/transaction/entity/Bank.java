package com.payment.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "banks",
    indexes = {
        @Index(name = "idx_bank_swift_code",   columnList = "swift_code",    unique = true),
        @Index(name = "idx_bank_routing_number", columnList = "routing_number", unique = true)
    }
)
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable full legal name of the bank. */
    @Column(name = "name", nullable = false)
    private String name;

    /** Short code / alias used internally (e.g. "LOCAL_BANK", "CHASE_US"). */
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    /**
     * SWIFT / BIC code (8 or 11 chars).
     * Used when routing inter-bank payments internationally.
     */
    @Column(name = "swift_code", nullable = false, unique = true, length = 11)
    private String swiftCode;

    /**
     * Domestic routing / ABA number.
     * Used for local bank-to-bank transfers.
     */
    @Column(name = "routing_number", length = 20)
    private String routingNumber;

    /** ISO 3166-1 alpha-2 country code (e.g. "US", "GB", "NG"). */
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    /** Primary contact email or phone for the bank. */
    @Column(name = "primary_contact")
    private String primaryContact;

    @Column(name = "address")
    private String address;

    @Column(name = "time_zone")
    private String timeZone;

    /**
     * True when this is our own institution.
     * Only one bank should have this flag set to true.
     */
    @Column(name = "is_local", nullable = false)
    private boolean local = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BankStatus status = BankStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BankStatus {
        ACTIVE, SUSPENDED, CLOSED
    }
    
}
