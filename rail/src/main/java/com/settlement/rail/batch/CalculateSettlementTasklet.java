package com.settlement.rail.batch;

import com.settlement.rail.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Step 1 \u2014 Calculate Settlement. A thin wrapper: the cycle id comes in as a
 * job parameter (an input identifier, not business data), and all the real
 * work is delegated to {@link SettlementService#calculateSettlement}.
 */
@Component
@RequiredArgsConstructor
public class CalculateSettlementTasklet implements Tasklet {

    private final SettlementService settlementService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long cycleId = chunkContext.getStepContext().getStepExecution().getJobParameters().getLong("cycleId");
        settlementService.calculateSettlement(cycleId);
        return RepeatStatus.FINISHED;
    }
}
