package com.payment.transaction.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        String currency
) {}
