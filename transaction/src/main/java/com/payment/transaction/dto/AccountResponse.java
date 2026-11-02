package com.payment.transaction.dto;

import com.payment.transaction.entity.Account;

public record AccountResponse(Long id, String accountNumber, String status, Long userId) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getStatus().name(),
                account.getUser().getId());
    }
}
