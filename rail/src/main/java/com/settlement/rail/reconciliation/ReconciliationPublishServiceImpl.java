package com.settlement.rail.reconciliation;

import com.settlement.rail.cycle.ReconciliationStatus;
import com.settlement.rail.outbox.OutboxEvent;
import com.settlement.rail.outbox.OutboxEventRepository;
import com.settlement.rail.settlement.Settlement;
import com.settlement.rail.settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationPublishServiceImpl implements ReconciliationPublishService {

    private static final String RECONCILIATION_TOPIC = "settlement.reconciliation.events";

    private final SettlementRepository settlementRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public void publishPendingReconciliations() {
        List<Settlement> pending =
                settlementRepository.findAllByReconciliationStatus(ReconciliationStatus.PENDING);

        for (Settlement settlement : pending) {
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("SETTLEMENT_RECONCILIATION")
                    .aggregateId(settlement.getId().toString())
                    .eventType("SETTLEMENT_RECONCILIATION_REQUESTED")
                    .topic(RECONCILIATION_TOPIC)
                    .payload(buildPayload(settlement))
                    .build());

            settlement.setReconciliationStatus(ReconciliationStatus.SENT);
            settlementRepository.save(settlement);
        }

        if (!pending.isEmpty()) {
            log.info("Queued {} settlement(s) for reconciliation", pending.size());
        }
    }

    private String buildPayload(Settlement settlement) {
        return """
                {"cycleId":"%s","bankCode":"%s","currency":"%s","netAmount":"%s","transactionCount":%d,"generatedAt":"%s"}"""
                .formatted(
                        settlement.getCycleId(),
                        settlement.getBankCode(),
                        settlement.getCurrency(),
                        settlement.getNetAmount().toPlainString(),
                        settlement.getTransactionCount(),
                        settlement.getCreatedAt()
                );
    }
}

