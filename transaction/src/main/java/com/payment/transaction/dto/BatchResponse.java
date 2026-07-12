package com.payment.transaction.dto;

public record BatchResponse(
        Long batchId,
        String batchReference,
        String status,
        Integer totalPayments,
        Integer successfulPayments,
        Integer failedPayments) {}
