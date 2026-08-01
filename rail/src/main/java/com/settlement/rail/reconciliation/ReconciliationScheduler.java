package com.settlement.rail.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A separate schedule from {@link com.settlement.rail.cycle.SettlementCycleScheduler}:
 * periodically publishes every still-{@code PENDING} settlement net position
 * for reconciliation with its owning bank, via the outbox/Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private static final long TWO_MINUTES_MS = 2 * 60 * 1000L;

    private final ReconciliationPublishService reconciliationPublishService;

    @Scheduled(fixedRate = TWO_MINUTES_MS)
    public void runReconciliation() {
        try {
            reconciliationPublishService.publishPendingReconciliations();
        } catch (Exception e) {
            log.error("Reconciliation publish run failed", e);
        }
    }
}
