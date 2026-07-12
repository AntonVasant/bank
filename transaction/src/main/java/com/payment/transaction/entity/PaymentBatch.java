package com.payment.transaction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "payment_batches")
public class PaymentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(nullable = false)
    private Integer totalPayments;

    @Column(nullable = false)
    private Integer successfulPayments;

    @Column(nullable = false)
    private Integer failedPayments;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "batch", fetch = FetchType.LAZY)
    private List<Payment> payments;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (successfulPayments == null) successfulPayments = 0;
        if (failedPayments == null) failedPayments = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BatchStatus {
        PENDING, PROCESSING, COMPLETED, PARTIALLY_FAILED, FAILED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchReference() { return batchReference; }
    public void setBatchReference(String batchReference) { this.batchReference = batchReference; }

    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }

    public Integer getTotalPayments() { return totalPayments; }
    public void setTotalPayments(Integer totalPayments) { this.totalPayments = totalPayments; }

    public Integer getSuccessfulPayments() { return successfulPayments; }
    public void setSuccessfulPayments(Integer successfulPayments) { this.successfulPayments = successfulPayments; }

    public Integer getFailedPayments() { return failedPayments; }
    public void setFailedPayments(Integer failedPayments) { this.failedPayments = failedPayments; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<Payment> getPayments() { return payments; }
    public void setPayments(List<Payment> payments) { this.payments = payments; }
}
