package com.payment.transaction.dto;

import java.math.BigDecimal;

/**
 * Intra-bank:  sourceAccountId + destinationAccountId + amount
 * Inter-bank:  sourceAccountId + destinationBankCode + destinationAccountNumber + amount
 *              (destinationAccountId must be null)
 */
public record CreatePaymentRequest(
        String paymentReference,
        String destinationBankCode,      
        String destinationAccountNumber,
        String sourceAccountNumber,
        BigDecimal amount
) {}
