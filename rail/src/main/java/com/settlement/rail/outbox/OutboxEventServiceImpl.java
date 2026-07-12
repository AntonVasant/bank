package com.settlement.rail.outbox;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {

    private static final int MAX_RETRIES = 10;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Claims a batch of PENDING events: marks them PROCESSING inside a single
     * transaction so concurrent scheduler instances cannot pick the same rows
     * (SELECT FOR UPDATE SKIP LOCKED).
     */
    @Override
    @Transactional
    public List<OutboxEvent> claimPendingBatch(int batchSize) {
        List<OutboxEvent> events = outboxEventRepository.findPendingForUpdate(batchSize);
        if (events.isEmpty()) {
            return events;
        }
        Instant now = Instant.now();
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setProcessingStartedAt(now);
        }
        return outboxEventRepository.saveAll(events);
    }

    /**
     * Marks a single event PUBLISHED after successful Kafka delivery.
     */
    @Override
    @Transactional
    public void markPublished(Long id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("OutboxEvent not found: " + id));
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        outboxEventRepository.save(event);
    }

    /**
     * Finds events stuck in PROCESSING for longer than the threshold and either
     * resets them to PENDING (incrementing retryCount) or marks them FAILED once
     * MAX_RETRIES is exhausted.
     */
    @Override
    @Transactional
    public void resetStuckEvents() {
        Instant cutoff = Instant.now().minus(STUCK_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<OutboxEvent> stuck = outboxEventRepository.findStuckProcessingEvents(cutoff);
        for (OutboxEvent event : stuck) {
            if (event.getRetryCount() >= MAX_RETRIES) {
                event.setStatus(OutboxEventStatus.FAILED);
            } else {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setStatus(OutboxEventStatus.PENDING);
                event.setProcessingStartedAt(null);
            }
        }
        if (!stuck.isEmpty()) {
            outboxEventRepository.saveAll(stuck);
        }
    }
}
