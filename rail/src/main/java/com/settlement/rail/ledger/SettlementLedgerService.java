package com.settlement.rail.ledger;

/**
 * Posts settled payments to the Postgres double-entry ledger
 * ({@link LedgerTransaction} / {@link LedgerEntry}) \u2014 the immutable
 * financial ledger of record for the rail, replacing TigerBeetle. The
 * settlement job calls this once a cycle closes; it never recalculates
 * anything, it only replays the {@code Payment} rows recorded in real time
 * for that cycle.
 */
public interface SettlementLedgerService {

    /**
     * Posts every {@code PENDING} payment tagged with {@code cycleId} to the
     * ledger as one double-entry transaction each (debiting the sender
     * bank's reserve account, crediting the receiver's), then flips it to
     * {@code POSTED}. Restart-safe: already-{@code POSTED} payments are
     * skipped.
     */
    void postCycleToLedger(Long cycleId);
}
