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
        String reference,

        @Size(max = 500)
        String description,

        @Size(max = 64)
        String senderAccountNumber,

        @Size(max = 64)
        String beneficiaryAccountNumber
) {}
