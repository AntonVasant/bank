package com.payment.transaction.dto;

/**
 * Payload this bank posts to the notification (delivery) service's
 * {@code POST /api/reconciliation/result} endpoint, once it has
 * independently recomputed its own numbers for the cycle and compared them
 * against the rail's reported numbers.
 */
public record ReconciliationResultRequest(
        String cycleId,
        String bankCode,
        String currency,
        int railTransactionCount,
        String railNetAmount,
        int bankTransactionCount,
        String bankNetAmount,
        boolean matched,
        String checkedAt) {
}
