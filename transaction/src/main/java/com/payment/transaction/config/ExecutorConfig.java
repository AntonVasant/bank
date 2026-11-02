package com.payment.transaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class ExecutorConfig {

    /**
     * Virtual-thread-per-task executor (JDK 21+). Unlike the old
     * {@code ThreadPoolTaskExecutor}, this has no fixed pool size/queue —
     * each task gets its own cheap virtual thread, which is what actually
     * fixes the blocking-I/O-bound throughput ceiling the old 50/200 pool
     * ran into. There is no {@link java.util.concurrent.RejectedExecutionException}
     * backpressure anymore (see {@code GlobalExceptionHandler}'s note on this).
     */
    @Bean(name = "paymentExecutor")
    public Executor paymentExecutor() {
        ThreadFactory threadFactory = Thread.ofVirtual().name("payment-", 0).factory();
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }
}
