package com.settlement.rail.cycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures exactly one OPEN settlement cycle exists as soon as the
 * application finishes starting, rather than relying on whichever happens
 * first — the initial transaction or the initial scheduler tick — to
 * lazily create it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCycleInitializer implements ApplicationRunner {

    private final SettlementCycleService settlementCycleService;

    @Override
    public void run(ApplicationArguments args) {
        SettlementCycle cycle = settlementCycleService.getCurrentCycle();
        log.info("Startup check complete: OPEN settlement cycle id={} is ready to accept transactions", cycle.getId());
    }
}
