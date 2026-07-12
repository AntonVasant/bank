package com.payment.transaction.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        String paymentReference,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String status) {}
