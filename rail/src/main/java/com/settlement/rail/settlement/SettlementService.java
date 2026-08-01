package com.settlement.rail.settlement;

/**
 * All settlement business logic used by the Settlement batch job's three
 * steps. Tasklets are thin wrappers that simply delegate here \u2014 Spring
 * Batch itself only orchestrates job/step execution, retry and
 * restartability; it never holds business data.
 */
public interface SettlementService {

    /**
     * Step 1 \u2014 Calculate Settlement.
     * Reads all ledger entries for the given cycle, aggregates by bank and
     * currency, and persists one {@code PENDING} {@link Settlement} row per
     * bank/currency. A no-op if rows for this cycle already exist (so a
     * restarted step doesn't recompute/duplicate).
     */
    void calculateSettlement(Long cycleId);

    /**
     * Step 2 \u2014 Apply Settlement.
     * Reads every {@code PENDING} settlement (across any cycle, so a
     * crashed/partial run resumes automatically), applies its net amount to
     * the bank's reserve balance, and flips it to {@code APPLIED}. Relies
     * entirely on the {@link Settlement} table \u2014 never recalculates.
     */
    void applySettlement();

    /**
     * Step 3 \u2014 Create Outbox.
     * Reads every {@code APPLIED} settlement with {@code outboxCreated = false}
     * (across any cycle), queues one outbox event per row, and marks it
     * {@code outboxCreated = true}.
     */
    void createOutbox();
}
