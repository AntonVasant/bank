package com.settlement.rail.settlement;

/**
 * Pipeline status of a {@link Settlement} row \u2014 tracks how far Step 2
 * (Apply Settlement) has progressed for this bank/currency/cycle.
 */
public enum SettlementStatus {
    /** Calculated (Step 1) but not yet applied to the bank's reserve balance. */
    PENDING,
    /** Applied to the bank's reserve balance (Step 2 complete). */
    APPLIED
}
