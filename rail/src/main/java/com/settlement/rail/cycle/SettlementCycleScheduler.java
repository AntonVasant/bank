package com.settlement.rail.cycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCycleScheduler {

    private static final long FIVE_MINUTES_MS = 1 * 60 * 1000L;

    private final SettlementCycleService settlementCycleService;
    private final JobOperator jobOperator;
    private final Job settlementJob;

    /**
     * Runs every 5 minutes. Rotates the settlement cycle (OPEN -> PROCESSING,
     * opening the next OPEN cycle) inside a single transaction, then launches
     * the Settlement batch job (Calculate -> Apply -> Create Outbox) with the
     * old cycle's id as a job parameter. Spring Batch only orchestrates that
     * job — it never creates, rotates or closes cycles; a
     * {@link SettlementCycleCompletionListener} closes the cycle once the
     * job completes successfully.
     */
    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    public void runSettlementCycle() {
         Long cycleId = settlementCycleService.rotateCycle();
        if (cycleId == null) {
            log.info("No previous open settlement cycle found; nothing to settle on this run");
            return;
        }

        try {
            jobOperator.start(settlementJob, new JobParametersBuilder()
                    .addLong("cycleId", cycleId)
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception e) {
            log.error("Failed to launch settlementJob for cycle id={}; cycle remains PROCESSING and " +
                    "can be retried on the next run", cycleId, e);
        }
    }
}

