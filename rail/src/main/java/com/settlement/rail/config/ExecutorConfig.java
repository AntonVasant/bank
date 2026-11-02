package com.settlement.rail.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class ExecutorConfig {

    /**
     * Virtual-thread-per-task executor (JDK 21+) replacing the old bounded
     * 50/200 {@code ThreadPoolTaskExecutor}. No fixed pool size/queue, so
     * there's no more {@code CallerRunsPolicy} back-pressure either -
     * every submitted task gets its own virtual thread immediately.
     */
    @Bean(name = "paymentExecutor")
    public Executor paymentExecutor() {
        ThreadFactory threadFactory = Thread.ofVirtual().name("payment-", 0).factory();
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }
}
