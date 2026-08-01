package com.settlement.rail.cycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Defense-in-depth for the "exactly one OPEN cycle at a time" invariant:
 * a partial unique index that the database itself enforces, so even
 * concurrent instances of this service (or multiple app instances) cannot
 * both successfully insert an OPEN cycle. {@code SettlementCycleServiceImpl}
 * relies on this index to detect and recover from that race.
 * <p>
 * Created here (rather than relying solely on {@code ddl-auto: update`})
 * because Hibernate cannot express a partial/filtered unique index from
 * entity annotations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCycleSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureAtMostOneOpenCycle() {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_cycle_one_open
                ON settlement_cycles (status)
                WHERE status = 'OPEN'
                """);
        log.info("Ensured at-most-one-OPEN-cycle unique index exists on settlement_cycles");
    }
}
