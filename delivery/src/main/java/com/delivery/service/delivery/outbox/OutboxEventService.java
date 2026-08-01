package com.delivery.service.delivery.outbox;

import java.util.List;

public interface OutboxEventService {

    List<OutboxEvent> claimPendingBatch(int batchSize);

    void markPublished(Long id);

    void resetStuckEvents();
}
