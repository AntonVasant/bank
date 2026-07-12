package com.settlement.rail.bank;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankRequest(
        @NotBlank @Size(max = 20)
        String bankCode,

        @NotBlank @Size(min = 2, max = 3)
        String country,

        @NotBlank @Size(max = 255)
        String name,

        String description,

        @Email @Size(max = 255)
        String contactEmail,

        @Size(max = 50)
        String contactPhone,

        @Size(max = 255)
        String website,

        @Size(max = 11)
        String swiftCode
) {}
