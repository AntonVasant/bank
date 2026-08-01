package com.settlement.rail.cycle;

/**
 * Tracks whether a bank's net settlement position for a cycle has been
 * published for reconciliation with that bank yet (separate from
 * {@code SettlementStatus}, which tracks the reserve-balance application),
 * and — once the bank reports back its own independently-computed result —
 * whether the two sides' numbers actually matched.
 */
public enum ReconciliationStatus {
    /** Not yet published for reconciliation. */
    PENDING,
    /** Published to the reconciliation topic for the notification service to deliver to the bank. */
    SENT,
    /** The bank reported back that its own numbers matched the rail's. */
    MATCHED,
    /** The bank reported back that its own numbers did NOT match the rail's — needs investigation. */
    MISMATCHED
}
