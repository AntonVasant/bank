package com.payment.transaction.dto;

/**
 * Payload received from the notification (delivery) service's
 * {@code POST /api/reconciliation} call, carrying the rail's calculated
 * per-cycle net position for this bank.
 */
public record ReconciliationRequest(
        String cycleId,
        String bankCode,
        String currency,
        String netAmount,
        int transactionCount) {
}
