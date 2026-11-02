package com.payment.transaction.dto;

import com.payment.transaction.entity.Payment;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        String paymentReference,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String status) {
    /**
     * Builds the response from an already-loaded {@link Payment}. Callers
     * must ensure {@code sourceAccount} (and {@code destinationAccount}, if
     * present) are already initialized — both are {@code FetchType.LAZY} —
     * before invoking this outside the originating transaction, otherwise
     * this throws {@code LazyInitializationException}.
     */
    public static PaymentResponse from(Payment payment) {
        String destinationAccountNumber = payment.getDestinationAccount() != null
                ? payment.getDestinationAccount().getAccountNumber()
                : payment.getDestinationExternalAccount();
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getSourceAccount().getAccountNumber(),
                destinationAccountNumber,
                payment.getAmount(),
                payment.getStatus().name());
    }
}
