package com.settlement.rail.batch;

import com.settlement.rail.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Step 2 \u2014 Apply Settlement. A thin wrapper: relies entirely on the
 * {@code Settlement} table populated by Step 1 \u2014 never recalculates. If the
 * process crashed after Step 1, this step simply reads the still-{@code PENDING}
 * rows and continues.
 */
@Component
@RequiredArgsConstructor
public class ApplySettlementTasklet implements Tasklet {

    private final SettlementService settlementService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        settlementService.applySettlement();
        return RepeatStatus.FINISHED;
    }
}
