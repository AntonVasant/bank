package com.settlement.rail.bank;

import lombok.Builder;

import java.time.Instant;

@Builder
public record BankResponse(
        Long id,
        String bankCode,
        String country,
        String name,
        String description,
        String contactEmail,
        String contactPhone,
        String website,
        String swiftCode,
        BankStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static BankResponse from(Bank bank) {
        return BankResponse.builder()
                .id(bank.getId())
                .bankCode(bank.getBankCode())
                .country(bank.getCountry())
                .name(bank.getName())
                .description(bank.getDescription())
                .contactEmail(bank.getContactEmail())
                .contactPhone(bank.getContactPhone())
                .website(bank.getWebsite())
                .swiftCode(bank.getSwiftCode())
                .status(bank.getStatus())
                .createdAt(bank.getCreatedAt())
                .updatedAt(bank.getUpdatedAt())
                .build();
    }
}
