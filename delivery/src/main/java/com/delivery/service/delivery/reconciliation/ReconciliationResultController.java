package com.delivery.service.delivery.reconciliation;

import com.delivery.service.delivery.outbox.OutboxEvent;
import com.delivery.service.delivery.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Receives a bank's independently-computed reconciliation result, records
 * it in this service's own {@link ReconciliationRecord} table, and relays
 * it to the rail (via the outbox/Kafka) so the rail can record whether the
 * bank's numbers matched its own.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReconciliationResultController {

    private static final String RESULT_TOPIC = "settlement.reconciliation.result.events";

    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final OutboxEventRepository outboxEventRepository;

    @PostMapping("/api/reconciliation/result")
    @Transactional
    public ResponseEntity<Void> receiveResult(@RequestBody ReconciliationResultRequest request) {
        log.info("Reconciliation result received cycleId='{}' bankCode='{}' currency='{}' matched={}",
                request.cycleId(), request.bankCode(), request.currency(), request.matched());

        reconciliationRecordRepository.save(ReconciliationRecord.builder()
                .cycleId(request.cycleId())
                .bankCode(request.bankCode())
                .currency(request.currency())
                .railTransactionCount(request.railTransactionCount())
                .railNetAmount(new BigDecimal(request.railNetAmount()))
                .bankTransactionCount(request.bankTransactionCount())
                .bankNetAmount(new BigDecimal(request.bankNetAmount()))
                .matched(request.matched())
                .checkedAt(Instant.parse(request.checkedAt()))
                .build());

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("SETTLEMENT_RECONCILIATION_RESULT")
                .aggregateId(request.cycleId() + ":" + request.bankCode() + ":" + request.currency())
                .eventType("SETTLEMENT_RECONCILIATION_RESULT_RECEIVED")
                .topic(RESULT_TOPIC)
                .payload(buildPayload(request))
                .build());

        return ResponseEntity.accepted().build();
    }

    private String buildPayload(ReconciliationResultRequest request) {
        return """
                {"cycleId":"%s","bankCode":"%s","currency":"%s","railTransactionCount":%d,"railNetAmount":"%s",\
                "bankTransactionCount":%d,"bankNetAmount":"%s","matched":%b,"checkedAt":"%s"}"""
                .formatted(
                        request.cycleId(), request.bankCode(), request.currency(),
                        request.railTransactionCount(), request.railNetAmount(),
                        request.bankTransactionCount(), request.bankNetAmount(),
                        request.matched(), request.checkedAt()
                );
    }
}
