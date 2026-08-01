package com.settlement.rail.reconciliation;

/**
 * Publishes each bank's still-{@code PENDING} settlement net position for
 * reconciliation (via the outbox/Kafka), so the notification service can
 * deliver it to the owning bank and the bank can compare it against its own
 * independently-calculated numbers.
 */
public interface ReconciliationPublishService {

    /**
     * Queues an outbox event for every net position (across any cycle) whose
     * reconciliation status is still {@code PENDING}, then marks each one
     * {@code SENT}. Safe to call repeatedly \u2014 a crashed/partial run simply
     * leaves the remaining positions {@code PENDING} for the next call.
     */
    void publishPendingReconciliations();
}
