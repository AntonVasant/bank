package com.payment.transaction.dto;

import java.math.BigDecimal;

public record SettlementRailRequest(
        String paymentReference,
        String sourceBankCode,
        String destinationAccountNumber,
        String destinationBankCode,
        BigDecimal amount,
        String currency,
        String sourceAccountNumber,
        long cycleId
) {}
