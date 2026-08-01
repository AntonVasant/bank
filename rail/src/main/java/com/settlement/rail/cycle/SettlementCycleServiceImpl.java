package com.settlement.rail.cycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Slf4j
@Service
@Transactional(readOnly = true)
public class SettlementCycleServiceImpl implements SettlementCycleService {

    /** Broadcast so every commercial bank can cache the currently open cycle id without asking the rail per-payment. */
    private static final String CYCLE_OPENED_TOPIC = "settlement.cycle.opened.events";

    private final SettlementCycleRepository settlementCycleRepository;
    private final TransactionTemplate newTransactionTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SettlementCycleServiceImpl(SettlementCycleRepository settlementCycleRepository,
                                       PlatformTransactionManager transactionManager,
                                       KafkaTemplate<String, String> kafkaTemplate) {
        this.settlementCycleRepository = settlementCycleRepository;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public SettlementCycle getCurrentCycle() {
        return settlementCycleRepository
                .findFirstByStatusOrderByStartTimeDesc(SettlementCycleStatus.OPEN)
                .orElseGet(this::openNewCycleSafely);
    }

    @Override
    public Long rotateCycle() {
        // Close the old cycle in its own transaction and let it fully commit
        // *before* opening the replacement. openNewCycleSafely() inserts the
        // new OPEN row in a separate (REQUIRES_NEW) transaction/connection —
        // if the close above were still part of an uncommitted transaction,
        // that separate connection would still see the old row as OPEN
        // (uncommitted writes aren't visible across connections) and the
        // insert would always hit the partial unique index, every time,
        // not just under real concurrency. Uses the same programmatic
        // TransactionTemplate as openNewCycleSafely() (rather than
        // @Transactional) so this genuinely commits here — a @Transactional
        // method called on `this` from within the same class bypasses
        // Spring's AOP proxy entirely (the classic self-invocation pitfall).
        Long closedCycleId = newTransactionTemplate.execute(status ->
                settlementCycleRepository
                        .findFirstByStatusOrderByStartTimeDesc(SettlementCycleStatus.OPEN)
                        .map(cycle -> {
                            cycle.setStatus(SettlementCycleStatus.PROCESSING);
                            cycle.setEndTime(Instant.now());
                            settlementCycleRepository.save(cycle);
                            return cycle.getId();
                        })
                        .orElse(null));

        if (closedCycleId != null) {
            openNewCycleSafely();
        }
        return closedCycleId;
    }

    @Override
    @Transactional
    public void completeCycle(Long cycleId) {
        settlementCycleRepository.findById(cycleId).ifPresent(cycle -> {
            cycle.setStatus(SettlementCycleStatus.CLOSED);
            settlementCycleRepository.save(cycle);
        });
    }

    /**
     * Opens a new OPEN cycle in its own fresh (REQUIRES_NEW) transaction. If
     * a concurrent caller (another thread, or another instance of this
     * service) already created the OPEN cycle first, the unique index on
     * {@code settlement_cycles(status) WHERE status = 'OPEN'} rejects this
     * insert; since the failure is isolated to this suspended transaction,
     * the caller's own transaction is unaffected and we simply look up the
     * cycle the other caller created instead of failing.
     */
    private SettlementCycle openNewCycleSafely() {
        try {
            SettlementCycle newCycle = newTransactionTemplate.execute(status ->
                    settlementCycleRepository.save(SettlementCycle.builder()
                            .startTime(Instant.now())
                            .status(SettlementCycleStatus.OPEN)
                            .build()));
            log.info("Opened new settlement cycle id={}", newCycle.getId());
            broadcastCycleOpened(newCycle.getId());
            return newCycle;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent attempt to open the OPEN settlement cycle detected; " +
                    "using the cycle created by the other transaction instead");
            return settlementCycleRepository.findFirstByStatusOrderByStartTimeDesc(SettlementCycleStatus.OPEN)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Broadcasts the newly-opened cycle id to every commercial bank via
     * Kafka, so each bank can cache it (in Redis) and tag its real-time
     * ledger holds with it directly — without asking the rail for
     * the current cycle id on every single payment. Best-effort: a bank that
     * misses this (e.g. was down) still gets the correct cycle id back on
     * its next payment attempt via the cycle-mismatch rejection path.
     */
    private void broadcastCycleOpened(Long cycleId) {
        try {
            kafkaTemplate.send(CYCLE_OPENED_TOPIC, String.valueOf(cycleId), "{\"cycleId\":%d}".formatted(cycleId));
        } catch (Exception e) {
            log.warn("Failed to broadcast cycle-opened event for cycleId={}; commercial banks will pick it up " +
                    "via the cycle-mismatch rejection path on their next payment instead", cycleId, e);
        }
    }
}
