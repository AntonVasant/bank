package com.settlement.rail.cycle;

public interface SettlementCycleService {

    /**
     * Returns the currently OPEN cycle, opening a new one first if none
     * exists yet (e.g. right after startup). Used to tag new transactions
     * (transfers, ledger entries) with the cycle they belong to.
     */
    SettlementCycle getCurrentCycle();

    /**
     * Inside a single transaction: finds the currently OPEN cycle, marks it
     * PROCESSING, and opens the next OPEN cycle so new transactions keep
     * flowing into it while the old one is being settled.
     *
     * @return the id of the cycle that was just moved to PROCESSING, or
     *         {@code null} if there was no OPEN cycle yet (e.g. first run
     *         after startup).
     */
    Long rotateCycle();

    /**
     * Marks the given cycle CLOSED once its settlement job has completed
     * successfully.
     */
    void completeCycle(Long cycleId);
}
