package com.settlement.rail.batch;

import com.settlement.rail.ledger.SettlementLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Step 2 \u2014 Post To Ledger. A thin wrapper: posts every {@code PENDING}
 * payment recorded in real time for this cycle to the Postgres ledger (the
 * immutable, double-entry ledger of record), independently of the net
 * position calculated in {@link CalculateSettlementTasklet}. If the process
 * crashed after this step, already-posted payments are skipped and this
 * step simply resumes with the still-{@code PENDING} ones.
 */
@Component
@RequiredArgsConstructor
public class PostPaymentsToLedgerTasklet implements Tasklet {

    private final SettlementLedgerService settlementLedgerService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long cycleId = chunkContext.getStepContext().getStepExecution().getJobParameters().getLong("cycleId");
        settlementLedgerService.postCycleToLedger(cycleId);
        return RepeatStatus.FINISHED;
    }
}
