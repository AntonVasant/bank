package com.settlement.rail.outbox;

import java.util.List;

public interface OutboxEventService {

    List<OutboxEvent> claimPendingBatch(int batchSize);

    void markPublished(Long id);

    void resetStuckEvents();
}
