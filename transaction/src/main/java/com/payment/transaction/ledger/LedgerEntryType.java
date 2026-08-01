package com.payment.transaction.ledger;

/**
 * The two sides of a double-entry {@link LedgerTransaction} line.
 */
public enum LedgerEntryType {
    /** Money leaves the referenced account. */
    DEBIT,
    /** Money enters the referenced account. */
    CREDIT
}
