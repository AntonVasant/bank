package com.settlement.rail.ledger;

/**
 * Lifecycle of a {@link LedgerTransaction}. The rail always creates these
 * as an immediate, non-pending double-entry posting (there's no hold phase
 * for a settlement posting), so in practice every row is created directly
 * as {@code POSTED}.
 */
public enum LedgerTransactionStatus {
    /** A hold has been placed but not yet finalized (unused by the rail today, kept for parity). */
    PENDING,
    /** Finalized as a permanent double-entry posting. */
    POSTED,
    /** Released \u2014 no funds ever moved (unused by the rail today, kept for parity). */
    VOIDED
}
