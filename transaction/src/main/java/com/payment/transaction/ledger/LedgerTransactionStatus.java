package com.payment.transaction.ledger;

/**
 * Lifecycle of a {@link LedgerTransaction} — mirrors the hold/post/void
 * two-phase semantics that used to be provided by TigerBeetle's
 * {@code PENDING}/{@code POST_PENDING_TRANSFER}/{@code VOID_PENDING_TRANSFER}
 * transfer flags, now implemented directly on top of Postgres.
 */
public enum LedgerTransactionStatus {
    /** A hold has been placed (funds locked) but not yet finalized. */
    PENDING,
    /** Finalized as a permanent double-entry posting. */
    POSTED,
    /** Released — no funds ever moved. */
    VOIDED
}
