package com.settlement.rail.ledger;

/**
 * The two sides of a double-entry {@link LedgerTransaction} line.
 */
public enum LedgerEntryType {
    /** Money leaves the referenced bank reserve account. */
    DEBIT,
    /** Money enters the referenced bank reserve account. */
    CREDIT
}
