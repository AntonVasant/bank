package com.payment.transaction.dto;

import java.math.BigDecimal;

public record BulkPaymentItem(
        String paymentReference,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount) {}
