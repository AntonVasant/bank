package com.delivery.service.delivery.grpc;

import com.delivery.service.delivery.config.BankGrpcEndpointProperties;
import com.settlement.rail.grpc.SettlementCycleReport;
import com.settlement.rail.grpc.SettlementReportAck;
import com.settlement.rail.grpc.SettlementReportServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC client that delivers a bank's settlement cycle net position — consumed
 * from the {@code settlement.net-position.events} Kafka topic — directly to
 * that bank's own {@code SettlementReportService} endpoint, so the bank can
 * update its own records. The bank acknowledges the report synchronously.
 * <p>
 * A {@link ManagedChannel} is created lazily per bank code and reused across
 * calls. If no endpoint is configured for a given bank code the call is
 * skipped and a warning is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReportGrpcClient {

    private final BankGrpcEndpointProperties endpointProperties;

    /** Lazily-created, per-bank-code channels. */
    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    /**
     * Sends a {@code ReportSettlementCycle} RPC to the bank identified by
     * {@code bankCode}.
     *
     * @return the bank's acknowledgement, or {@code null} if no endpoint is
     *         configured for that bank.
     */
    public SettlementReportAck sendSettlementReport(String cycleId, String bankCode, String currency,
                                                      String netAmount, int transactionCount, String generatedAt) {
        BankGrpcEndpointProperties.Endpoint endpoint = endpointProperties.getEndpoints().get(bankCode);

        if (endpoint == null) {
            log.warn("No gRPC endpoint configured for bank='{}'; skipping settlement report for cycleId='{}'",
                    bankCode, cycleId);
            return null;
        }

        ManagedChannel channel = channels.computeIfAbsent(bankCode,
                k -> ManagedChannelBuilder
                        .forAddress(endpoint.getHost(), endpoint.getPort())
                        .usePlaintext()
                        .build());

        SettlementReportServiceGrpc.SettlementReportServiceBlockingStub stub =
                SettlementReportServiceGrpc.newBlockingStub(channel);

        SettlementCycleReport report = SettlementCycleReport.newBuilder()
                .setCycleId(cycleId)
                .setBankCode(bankCode)
                .setCurrency(currency)
                .setNetAmount(netAmount)
                .setTransactionCount(transactionCount)
                .setGeneratedAt(generatedAt)
                .build();

        log.debug("Sending settlement report to bank='{}' host='{}:{}' cycleId='{}'",
                bankCode, endpoint.getHost(), endpoint.getPort(), cycleId);

        try {
            SettlementReportAck ack = stub.reportSettlementCycle(report);
            log.info("Settlement report acknowledged by bank='{}' ackId='{}' accepted={} cycleId='{}'",
                    bankCode, ack.getAckId(), ack.getAccepted(), cycleId);
            return ack;
        } catch (StatusRuntimeException e) {
            log.error("gRPC settlement report to bank='{}' failed for cycleId='{}': status={}",
                    bankCode, cycleId, e.getStatus(), e);
            return null;
        }
    }

    @PreDestroy
    void shutdownChannels() {
        channels.values().forEach(ManagedChannel::shutdown);
    }
}
