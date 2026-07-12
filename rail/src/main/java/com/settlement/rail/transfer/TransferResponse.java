package com.settlement.rail.transfer;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record TransferResponse(
        Long id,
        String reference,
        Long fromBankId,
        String fromBankCode,
        Long toBankId,
        String toBankCode,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String description,
        String senderAccountNumber,
        String beneficiaryAccountNumber,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    static TransferResponse from(Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .reference(transfer.getReference())
                .fromBankId(transfer.getFromBank().getId())
                .fromBankCode(transfer.getFromBank().getBankCode())
                .toBankId(transfer.getToBank().getId())
                .toBankCode(transfer.getToBank().getBankCode())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .status(transfer.getStatus())
                .description(transfer.getDescription())
                .senderAccountNumber(transfer.getSenderAccountNumber())
                .beneficiaryAccountNumber(transfer.getBeneficiaryAccountNumber())
                .failureReason(transfer.getFailureReason())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .completedAt(transfer.getCompletedAt())
                .build();
    }
}
