package com.payment.transaction.dto;

import com.payment.transaction.entity.PaymentBatch;

public record BatchResponse(
        Long batchId,
        String batchReference,
        String status,
        Integer totalPayments,
        Integer successfulPayments,
        Integer failedPayments) {
    public static BatchResponse from(PaymentBatch batch) {
        return new BatchResponse(
                batch.getId(),
                batch.getBatchReference(),
                batch.getStatus().name(),
                batch.getTotalPayments(),
                batch.getSuccessfulPayments(),
                batch.getFailedPayments());
    }
}
