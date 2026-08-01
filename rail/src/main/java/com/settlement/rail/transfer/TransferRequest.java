package com.settlement.rail.transfer;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Size(max = 20)
        String fromBankCode,

        @NotBlank @Size(max = 20)
        String toBankCode,

        @NotNull @DecimalMin(value = "0.000001", inclusive = true) @Digits(integer = 14, fraction = 6)
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        String currency,

        @NotBlank @Size(max = 64)
        String utr,

        @Size(max = 500)
        String description,

        @Size(max = 64)
        String senderAccountNumber,

        @Size(max = 64)
        String beneficiaryAccountNumber,

        /**
         * The settlement cycle id the sending bank cached from the central
         * bank's last cycle-open broadcast. Optional (null/blank means "not
         * supplied" — e.g. callers not yet updated to this protocol): when
         * present, {@code TransferServiceImpl.initiate} rejects the request
         * if it no longer matches the currently open cycle, rather than
         * silently accepting a stale cycle id.
         */
        Long cycleId
) {}
