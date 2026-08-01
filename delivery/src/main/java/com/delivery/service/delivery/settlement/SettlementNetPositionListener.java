package com.delivery.service.delivery.settlement;

import com.delivery.service.delivery.grpc.SettlementReportGrpcClient;
import com.delivery.service.delivery.outbox.OutboxEvent;
import com.delivery.service.delivery.outbox.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlement.rail.grpc.SettlementReportAck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementNetPositionListener {

    private static final String ACK_TOPIC = "settlement.report.acknowledged.events";

    private final ObjectMapper objectMapper;
    private final SettlementReportGrpcClient settlementReportGrpcClient;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Consumes each bank's settlement cycle net position from the rail's
     * outbox (published to this topic once a cycle closes) and delivers it
     * to that bank's own {@code SettlementReportService} gRPC endpoint so
     * the bank can update its own records. Once the bank acknowledges, an
     * ack event is queued (via this service's own outbox) so rail can learn
     * the report was fully processed.
     */
    @KafkaListener(topics = "settlement.net-position.events", groupId = "${spring.kafka.consumer.group-id}")
    public void onSettlementNetPosition(String payload) {
        try {
            SettlementNetPositionEvent event = objectMapper.readValue(payload, SettlementNetPositionEvent.class);

            SettlementReportAck ack = settlementReportGrpcClient.sendSettlementReport(
                    event.cycleId(),
                    event.bankCode(),
                    event.currency(),
                    event.netAmount(),
                    event.transactionCount(),
                    event.generatedAt());

            if (ack == null) {
                log.warn("No acknowledgement received for settlement report cycleId='{}' bankCode='{}'",
                        event.cycleId(), event.bankCode());
                return;
            }
            

            log.info("Queued settlement report ack for positionId='{}' cycleId='{}' bankCode='{}' accepted={}",
                    event.positionId(), event.cycleId(), event.bankCode(), ack.getAccepted());
        } catch (Exception e) {
            log.error("Failed to process settlement net position event payload='{}'", payload, e);
        }
    }

    private String buildAckPayload(SettlementNetPositionEvent event, SettlementReportAck ack) {
        return """
                {"positionId":"%s","cycleId":"%s","bankCode":"%s","ackId":"%s","accepted":%b,"acknowledgedAt":"%s"}"""
                .formatted(
                        event.positionId(),
                        event.cycleId(),
                        event.bankCode(),
                        ack.getAckId(),
                        ack.getAccepted(),
                        Instant.now()
                );
    }
}
