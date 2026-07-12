package com.payment.transaction.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        String paymentReference,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String status) {}
