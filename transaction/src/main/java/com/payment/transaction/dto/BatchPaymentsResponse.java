package com.payment.transaction.dto;

import java.util.List;

public record BatchPaymentsResponse(Long batchId, List<PaymentSummary> payments) {

    public record PaymentSummary(Long paymentId, String paymentReference, String status) {}
}
