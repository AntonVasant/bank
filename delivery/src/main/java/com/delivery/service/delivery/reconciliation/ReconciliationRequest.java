package com.delivery.service.delivery.reconciliation;

/**
 * Payload delivered to a bank's {@code POST /api/reconciliation} endpoint,
 * carrying the rail's calculated per-cycle net position for that bank.
 */
public record ReconciliationRequest(
        String cycleId,
        String bankCode,
        String currency,
        String netAmount,
        int transactionCount) {
}
