package com.settlement.rail.bank;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record BankReserveResponse(
        Long id,
        Long bankId,
        String bankCode,
        String currency,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        Instant createdAt,
        Instant updatedAt
) {
    static BankReserveResponse from(BankReserve reserve) {
        return BankReserveResponse.builder()
                .id(reserve.getId())
                .bankId(reserve.getBank().getId())
                .bankCode(reserve.getBank().getBankCode())
                .currency(reserve.getCurrency())
                .balance(reserve.getBalance())
                .reservedBalance(reserve.getReservedBalance())
                .availableBalance(reserve.getAvailableBalance())
                .createdAt(reserve.getCreatedAt())
                .updatedAt(reserve.getUpdatedAt())
                .build();
    }
}
