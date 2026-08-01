package com.settlement.rail.cycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Registered on {@code settlementJob}. Spring Batch itself never creates,
 * rotates or closes settlement cycles — it only reports whether the job for
 * a given cycle id succeeded. This listener is the single place that reacts
 * to that outcome by closing the cycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCycleCompletionListener implements JobExecutionListener {

    private final SettlementCycleService settlementCycleService;

    @Override
    public void afterJob(JobExecution jobExecution) {
        Long cycleId = jobExecution.getJobParameters().getLong("cycleId");
        if (cycleId == null) {
            return;
        }

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            settlementCycleService.completeCycle(cycleId);
            log.info("Settlement cycle id={} marked CLOSED after successful settlementJob completion", cycleId);
        } else {
            log.error("settlementJob for cycle id={} finished with status={}; cycle remains PROCESSING and " +
                    "can be retried on the next run", cycleId, jobExecution.getStatus());
        }
    }
}
