package com.payment.transaction.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountNumber,
        BigDecimal balance,
        String currency
) {}
