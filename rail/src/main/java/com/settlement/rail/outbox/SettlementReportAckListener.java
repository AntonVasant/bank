package com.settlement.rail.outbox;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReportAckListener {

    private final ObjectMapper objectMapper;
    private final OutboxEventService outboxEventService;

    /**
     * Consumes the notification service's acknowledgement — published once the
     * bank confirms it received and processed a settlement net position — and
     * marks the originating {@code SETTLEMENT_NET_POSITION} outbox row as
     * ACKNOWLEDGED so rail knows the event was fully processed end-to-end.
     */
    @KafkaListener(topics = "settlement.report.acknowledged.events", groupId = "${spring.kafka.consumer.group-id}")
    public void onSettlementReportAcknowledged(String payload) {
        try {
            SettlementReportAckEvent event = objectMapper.readValue(payload, SettlementReportAckEvent.class);

            if (!event.accepted()) {
                log.warn("Bank='{}' rejected settlement report positionId='{}' cycleId='{}'; leaving outbox event as PUBLISHED",
                        event.bankCode(), event.positionId(), event.cycleId());
                return;
            }

            outboxEventService.markAcknowledged("SETTLEMENT_NET_POSITION", event.positionId());
            log.info("Settlement net position positionId='{}' cycleId='{}' bankCode='{}' acknowledged by bank (ackId='{}')",
                    event.positionId(), event.cycleId(), event.bankCode(), event.ackId());
        } catch (Exception e) {
            log.error("Failed to process settlement report ack payload='{}'", payload, e);
        }
    }

    /**
     * Maps the JSON payload published by the notification service's own
     * outbox for the {@code SETTLEMENT_REPORT_ACKNOWLEDGED} event type
     * (topic {@code settlement.report.acknowledged.events}).
     */
    private record SettlementReportAckEvent(
            @JsonProperty("positionId") String positionId,
            @JsonProperty("cycleId") String cycleId,
            @JsonProperty("bankCode") String bankCode,
            @JsonProperty("ackId") String ackId,
            @JsonProperty("accepted") boolean accepted) {
    }
}
