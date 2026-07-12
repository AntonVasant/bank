package com.payment.transaction.grpc;

import com.centralbank.gateway.v1.AckStatus;
import com.centralbank.gateway.v1.AckType;
import com.centralbank.gateway.v1.Acknowledgment;
import com.centralbank.gateway.v1.CentralBankGatewayGrpc;
import com.centralbank.gateway.v1.CreditRequest;
import com.centralbank.gateway.v1.TransferNotification;
import com.google.protobuf.Timestamp;
import com.payment.transaction.service.CreditOrchestrator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * gRPC service implementation for the Central Bank Gateway.
 * <p>
 * Receives {@code RequestCredit} and {@code SendTransferNotification} RPCs from
 * the central bank.
 * <ul>
 *   <li>{@code RequestCredit} — delegates all DB work to
 *       {@link CreditOrchestrator} on the {@code paymentExecutor} thread pool.</li>
 *   <li>{@code SendTransferNotification} — logs the outcome notification sent by
 *       the rail after it has notified the receiver bank, then returns a
 *       {@code NOTIFICATION_ACK}. Payment-state transitions are handled by the
 *       {@code ProcessPayment} streaming response path in
 *       {@link com.payment.transaction.service.SettlementRailClient}.</li>
 * </ul>
 * The gRPC I/O thread is <b>never blocked</b>.
 */
@Slf4j
@Service
public class CentralBankGatewayGrpcServer
        extends CentralBankGatewayGrpc.CentralBankGatewayImplBase {

    private final CreditOrchestrator creditOrchestrator;

    public CentralBankGatewayGrpcServer(CreditOrchestrator creditOrchestrator) {
        this.creditOrchestrator = creditOrchestrator;
    }

    @Override
    public void requestCredit(CreditRequest request,
                               StreamObserver<Acknowledgment> responseObserver) {
        creditOrchestrator.handleCreditRequest(request)
                .whenComplete((acknowledgment, ex) -> {
                    if (ex != null) {
                        responseObserver.onError(Status.INTERNAL
                                .withDescription("Unexpected error processing credit request: "
                                        + ex.getMessage())
                                .withCause(ex)
                                .asRuntimeException());
                    } else {
                        responseObserver.onNext(acknowledgment);
                        responseObserver.onCompleted();
                    }
                });
    }

    /**
     * Receives the rail's post-settlement notification for the sender bank.
     * <p>
     * The rail calls this after the receiver bank has acknowledged (or rejected)
     * the credit instruction. The notification carries the final
     * {@code TransferStatus} ({@code CREDITED} or {@code FAILED}) and the
     * settlement reference so the sender bank can reconcile its ledger.
     * <p>
     * Payment-state transitions (CB_ACKNOWLEDGED → COMPLETED / FAILED) are
     * driven by the {@code ProcessPayment} stream, so no DB work is done here —
     * we simply log and return a success acknowledgement to unblock the rail.
     */
    @Override
    public void sendTransferNotification(TransferNotification request,
                                          StreamObserver<Acknowledgment> responseObserver) {
        try {
            log.info("Transfer notification received: notificationId='{}' reference='{}' status='{}' " +
                     "from='{}' to='{}'",
                    request.getNotificationId(),
                    request.getReferenceNumber(),
                    request.getStatus(),
                    request.getSenderBankCode(),
                    request.getReceiverBankCode());

            Instant now = Instant.now();
            Acknowledgment ack = Acknowledgment.newBuilder()
                    .setAckId(UUID.randomUUID().toString())
                    .setReferenceId(request.getNotificationId())
                    .setAckType(AckType.NOTIFICATION_ACK)
                    .setStatus(AckStatus.SUCCESS)
                    .setReceivedAt(toProtoTimestamp(now))
                    .setProcessedAt(toProtoTimestamp(now))
                    .build();

            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Error processing transfer notification '{}': {}",
                    request.getNotificationId(), ex.getMessage(), ex);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error processing transfer notification: " + ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException());
        }
    }

    private static Timestamp toProtoTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
