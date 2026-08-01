package com.settlement.rail.payment;

/**
 * Whether a {@link Payment} has been posted to the Postgres ledger (the
 * immutable, double-entry financial ledger of record) yet.
 */
public enum PaymentLedgerStatus {
    /** Recorded in real time when the transfer was accepted; not yet posted to the ledger. */
    PENDING,
    /** Successfully posted to the ledger as a double-entry transaction. */
    POSTED,
    /** The ledger posting failed; needs investigation before it can be retried. */
    FAILED
}
