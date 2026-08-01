package com.settlement.rail.transfer;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record TransferResponse(
        Long id,
        String utr,
        Long fromBankId,
        String fromBankCode,
        Long toBankId,
        String toBankCode,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        CreditLegStatus creditStatus,
        String description,
        String senderAccountNumber,
        String beneficiaryAccountNumber,
        String cycleId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    static TransferResponse from(Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .utr(transfer.getUtr())
                .fromBankId(transfer.getFromBank().getId())
                .fromBankCode(transfer.getFromBank().getBankCode())
                .toBankId(transfer.getToBank().getId())
                .toBankCode(transfer.getToBank().getBankCode())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .status(transfer.getStatus())
                .creditStatus(transfer.getCreditStatus())
                .description(transfer.getDescription())
                .senderAccountNumber(transfer.getSenderAccountNumber())
                .beneficiaryAccountNumber(transfer.getBeneficiaryAccountNumber())
                .cycleId(transfer.getCycleId())
                .failureReason(transfer.getFailureReason())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .completedAt(transfer.getCompletedAt())
                .build();
    }

    /**
     * A rejection that never touched the database — used when the caller's
     * supplied cycle id no longer matches the currently open cycle. No
     * Transfer/Payment/reserve rows are created; {@code cycleId} carries the
     * real, currently-open cycle id so the caller can retry with the same
     * {@code utr} and the corrected cycle id. Zero-value defaults are used
     * for the normally-required fields (id, bank ids/codes, amount,
     * currency) since {@code toProto}/callers dereference them
     * unconditionally \u2014 there is no real transfer behind this response.
     */
    static TransferResponse cycleMismatch(String utr, Long actualOpenCycleId) {
        Instant now = Instant.now();
        return TransferResponse.builder()
                .id(0L)
                .utr(utr)
                .fromBankId(0L)
                .fromBankCode("")
                .toBankId(0L)
                .toBankCode("")
                .amount(BigDecimal.ZERO)
                .currency("")
                .status(TransferStatus.FAILED)
                .creditStatus(CreditLegStatus.PENDING)
                .failureReason("CYCLE_MISMATCH")
                .cycleId(String.valueOf(actualOpenCycleId))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
