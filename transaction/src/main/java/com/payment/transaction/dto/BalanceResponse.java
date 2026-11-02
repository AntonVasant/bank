package com.payment.transaction.dto;

import com.payment.transaction.entity.Account;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountNumber,
        BigDecimal balance,
        String currency
) {
    public static BalanceResponse from(Account account) {
        return new BalanceResponse(
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency().name());
    }
}
