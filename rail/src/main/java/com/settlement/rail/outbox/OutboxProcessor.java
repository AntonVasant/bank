package com.settlement.rail.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private static final int BATCH_SIZE = 50;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Polls PENDING outbox events, publishes each to its Kafka topic, then
     * marks it PUBLISHED — all without a surrounding transaction so the DB
     * commit from claimPendingBatch() is visible before the Kafka send.
     *
     * Runs 1 second after the previous execution completes.
     */
    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        List<OutboxEvent> claimed = outboxEventService.claimPendingBatch(BATCH_SIZE);
        for (OutboxEvent event : claimed) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outboxEventService.markPublished(event.getId());
                log.debug("Published outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} type={} — will be retried by stuck-events scheduler",
                        event.getId(), event.getEventType(), e);
                // Event remains PROCESSING; the stuck-events scheduler will reset it to PENDING
            }
        }
    }

    /**
     * Finds events stuck in PROCESSING for more than 5 minutes and resets them
     * to PENDING for retry, or marks them FAILED after 10 attempts.
     *
     * Runs 60 seconds after the previous execution completes.
     */
    @Scheduled(fixedDelay = 60_000)
    public void resetStuckEvents() {
        log.debug("Checking for stuck outbox events");
        outboxEventService.resetStuckEvents();
    }
}
