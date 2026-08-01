package com.settlement.rail.outbox;

import java.util.List;

public interface OutboxEventService {

    List<OutboxEvent> claimPendingBatch(int batchSize);

    void markPublished(Long id);

    void resetStuckEvents();

    /**
     * Marks the outbox event(s) matching the given aggregate as ACKNOWLEDGED,
     * e.g. once the notification service confirms the bank processed it.
     */
    void markAcknowledged(String aggregateType, String aggregateId);
}
