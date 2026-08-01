package com.settlement.rail.batch;

import com.settlement.rail.cycle.SettlementCycleCompletionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires the three-step Settlement job. Spring Batch here is responsible only
 * for orchestration \u2014 job/step sequencing, restartability and execution
 * metadata (stored in its own {@code BATCH_*} tables). All business state
 * lives in the {@code Settlement} (and downstream) application tables, never
 * in a Spring Batch {@code ExecutionContext}.
 */
@Configuration
@RequiredArgsConstructor
public class SettlementJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CalculateSettlementTasklet calculateSettlementTasklet;
    private final PostPaymentsToLedgerTasklet postPaymentsToLedgerTasklet;
    private final ApplySettlementTasklet applySettlementTasklet;
    private final CreateOutboxTasklet createOutboxTasklet;
    private final SettlementCycleCompletionListener settlementCycleCompletionListener;

    @Bean
    public Step calculateSettlementStep() {
        return new StepBuilder("calculateSettlementStep", jobRepository)
                .tasklet(calculateSettlementTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step postPaymentsToLedgerStep() {
        return new StepBuilder("postPaymentsToLedgerStep", jobRepository)
                .tasklet(postPaymentsToLedgerTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step applySettlementStep() {
        return new StepBuilder("applySettlementStep", jobRepository)
                .tasklet(applySettlementTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step createOutboxStep() {
        return new StepBuilder("createOutboxStep", jobRepository)
                .tasklet(createOutboxTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .listener(settlementCycleCompletionListener)
                .start(calculateSettlementStep())
                .next(postPaymentsToLedgerStep())
                .next(applySettlementStep())
                .next(createOutboxStep())
                .build();
    }
}
