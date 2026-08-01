package com.settlement.rail.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.rail.cycle.ReconciliationStatus;
import com.settlement.rail.settlement.Settlement;
import com.settlement.rail.settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationResultListener {

    private final ObjectMapper objectMapper;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final SettlementRepository settlementRepository;

    /**
     * Consumes the notification service's relay of a bank's independently
     * computed reconciliation outcome, persists it (audit trail), and marks
     * the corresponding {@link Settlement} row's {@link ReconciliationStatus}
     * {@code MATCHED}/{@code MISMATCHED} so the outcome is visible directly
     * on the settlement record itself, not just in the separate result log.
     * Logs a warning on mismatch so it's easy to spot in the rail's logs.
     */
    @KafkaListener(topics = "settlement.reconciliation.result.events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onReconciliationResult(String payload) {
        try {
            ReconciliationResultEvent event = objectMapper.readValue(payload, ReconciliationResultEvent.class);

            ReconciliationResult result = ReconciliationResult.builder()
                    .cycleId(event.cycleId())
                    .bankCode(event.bankCode())
                    .currency(event.currency())
                    .railTransactionCount(event.railTransactionCount())
                    .railNetAmount(new BigDecimal(event.railNetAmount()))
                    .bankTransactionCount(event.bankTransactionCount())
                    .bankNetAmount(new BigDecimal(event.bankNetAmount()))
                    .matched(event.matched())
                    .checkedAt(Instant.parse(event.checkedAt()))
                    .build();
            reconciliationResultRepository.save(result);

            markSettlementStatus(event);

            if (event.matched()) {
                log.info("Reconciliation MATCHED cycleId='{}' bankCode='{}' currency='{}'",
                        event.cycleId(), event.bankCode(), event.currency());
            } else {
                log.warn("Reconciliation MISMATCH cycleId='{}' bankCode='{}' currency='{}' " +
                                "rail(count={}, net={}) bank(count={}, net={})",
                        event.cycleId(), event.bankCode(), event.currency(),
                        event.railTransactionCount(), event.railNetAmount(),
                        event.bankTransactionCount(), event.bankNetAmount());
            }
        } catch (Exception e) {
            log.error("Failed to process reconciliation result payload='{}'", payload, e);
        }
    }

    private void markSettlementStatus(ReconciliationResultEvent event) {
        Long cycleId;
        try {
            cycleId = Long.valueOf(event.cycleId());
        } catch (NumberFormatException e) {
            log.warn("Unparseable cycleId='{}' in reconciliation result; cannot mark the Settlement row",
                    event.cycleId());
            return;
        }

        settlementRepository.findByCycleIdAndBankCodeAndCurrency(cycleId, event.bankCode(), event.currency())
                .ifPresentOrElse(settlement -> {
                    settlement.setReconciliationStatus(
                            event.matched() ? ReconciliationStatus.MATCHED : ReconciliationStatus.MISMATCHED);
                    settlementRepository.save(settlement);
                }, () -> log.warn("No Settlement row found for cycleId='{}' bankCode='{}' currency='{}'; " +
                                "reconciliation result recorded but Settlement status left unmarked",
                        event.cycleId(), event.bankCode(), event.currency()));
    }

    private record ReconciliationResultEvent(
            @JsonProperty("cycleId") String cycleId,
            @JsonProperty("bankCode") String bankCode,
            @JsonProperty("currency") String currency,
            @JsonProperty("railTransactionCount") int railTransactionCount,
            @JsonProperty("railNetAmount") String railNetAmount,
            @JsonProperty("bankTransactionCount") int bankTransactionCount,
            @JsonProperty("bankNetAmount") String bankNetAmount,
            @JsonProperty("matched") boolean matched,
            @JsonProperty("checkedAt") String checkedAt) {
    }
}
