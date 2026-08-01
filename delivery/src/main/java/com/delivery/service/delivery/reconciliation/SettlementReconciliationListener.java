package com.delivery.service.delivery.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReconciliationListener {

    private final ObjectMapper objectMapper;
    private final BankReconciliationRestClient bankReconciliationRestClient;

    /**
     * Consumes the rail's per-bank reconciliation request (published once
     * the rail's reconciliation schedule runs) and delivers it to the owning
     * bank over REST. The bank only needs to accept it here; the actual
     * comparison result comes back later via its own POST callback.
     */
    @KafkaListener(topics = "settlement.reconciliation.events", groupId = "${spring.kafka.consumer.group-id}")
    public void onSettlementReconciliation(String payload) {
        try {
            SettlementReconciliationEvent event = objectMapper.readValue(payload, SettlementReconciliationEvent.class);

            ReconciliationRequest request = new ReconciliationRequest(
                    event.cycleId(), event.bankCode(), event.currency(), event.netAmount(), event.transactionCount());

            bankReconciliationRestClient.sendReconciliationRequest(request);
        } catch (Exception e) {
            log.error("Failed to process settlement reconciliation event payload='{}'", payload, e);
        }
    }

    private record SettlementReconciliationEvent(
            @JsonProperty("cycleId") String cycleId,
            @JsonProperty("bankCode") String bankCode,
            @JsonProperty("currency") String currency,
            @JsonProperty("netAmount") String netAmount,
            @JsonProperty("transactionCount") int transactionCount,
            @JsonProperty("generatedAt") String generatedAt) {
    }
}
