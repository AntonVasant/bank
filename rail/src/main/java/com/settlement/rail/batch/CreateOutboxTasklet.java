package com.settlement.rail.batch;

import com.settlement.rail.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Step 3 \u2014 Create Outbox. A thin wrapper: reads {@code APPLIED} settlements
 * with {@code outboxCreated = false} and queues one outbox event per row.
 * The outbox publisher itself is not part of Spring Batch \u2014 it runs
 * independently (see {@code com.settlement.rail.outbox.OutboxProcessor}) and
 * publishes to Kafka.
 */
@Component
@RequiredArgsConstructor
public class CreateOutboxTasklet implements Tasklet {

    private final SettlementService settlementService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        settlementService.createOutbox();
        return RepeatStatus.FINISHED;
    }
}
