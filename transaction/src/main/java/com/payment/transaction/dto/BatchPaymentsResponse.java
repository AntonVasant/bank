package com.payment.transaction.dto;

import com.payment.transaction.entity.Payment;

import java.util.List;

public record BatchPaymentsResponse(Long batchId, List<PaymentSummary> payments) {

    public record PaymentSummary(Long paymentId, String paymentReference, String status) {}

    public static BatchPaymentsResponse from(Long batchId, List<Payment> payments) {
        List<PaymentSummary> summaries = payments.stream()
                .map(p -> new PaymentSummary(p.getId(), p.getPaymentReference(), p.getStatus().name()))
                .toList();
        return new BatchPaymentsResponse(batchId, summaries);
    }
}
