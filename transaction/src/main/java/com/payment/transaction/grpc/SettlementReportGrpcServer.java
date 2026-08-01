package com.payment.transaction.grpc;

import com.payment.transaction.service.SettlementCycleReportService;
import com.settlement.rail.grpc.SettlementCycleReport;
import com.settlement.rail.grpc.SettlementReportAck;
import com.settlement.rail.grpc.SettlementReportServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * gRPC service implementation for {@code SettlementReportService}.
 * <p>
 * Receives the {@code ReportSettlementCycle} RPC from the rail after each
 * settlement cycle closes, carrying this bank's net amount, transaction
 * count and when the net amount was generated. The report is persisted via
 * {@link SettlementCycleReportService} and acknowledged back to the rail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementReportGrpcServer extends SettlementReportServiceGrpc.SettlementReportServiceImplBase {

    private final SettlementCycleReportService settlementCycleReportService;

    @Override
    public void reportSettlementCycle(SettlementCycleReport request,
                                       StreamObserver<SettlementReportAck> responseObserver) {
        try {
            log.info("Settlement cycle report received: cycleId='{}' bankCode='{}' currency='{}' " +
                     "netAmount='{}' transactionCount={}",
                    request.getCycleId(), request.getBankCode(), request.getCurrency(),
                    request.getNetAmount(), request.getTransactionCount());

            settlementCycleReportService.recordReport(request);

            SettlementReportAck ack = SettlementReportAck.newBuilder()
                    .setAckId(UUID.randomUUID().toString())
                    .setAccepted(true)
                    .setMessage("Settlement cycle report recorded")
                    .build();

            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error processing settlement cycle report cycleId='{}': {}",
                    request.getCycleId(), e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error processing settlement cycle report: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
