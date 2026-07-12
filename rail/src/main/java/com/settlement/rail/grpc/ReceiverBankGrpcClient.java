package com.settlement.rail.grpc;

import com.centralbank.gateway.v1.Acknowledgment;
import com.centralbank.gateway.v1.AckStatus;
import com.centralbank.gateway.v1.CentralBankGatewayGrpc;
import com.centralbank.gateway.v1.CreditRequest;
import com.centralbank.gateway.v1.Money;
import com.centralbank.gateway.v1.TransferNotification;
import com.google.protobuf.Timestamp;
import com.settlement.rail.config.BankGrpcEndpointProperties;
import com.settlement.rail.transfer.TransferResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client that instructs a receiver bank to credit a beneficiary account.
 * <p>
 * A {@link ManagedChannel} is created lazily per bank code and reused across
 * calls. All channels are cleanly shut down on application shutdown via
 * {@link #shutdownChannels()}.
 * <p>
 * If no endpoint is configured for a given bank code the call is skipped and
 * a warning is logged — this allows partial deployments where not every bank
 * has been configured yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiverBankGrpcClient {

    private final BankGrpcEndpointProperties endpointProperties;

    /** Lazily-created, per-bank-code channels. */
    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    /**
     * Sends a {@code RequestCredit} RPC to the receiver bank identified by
     * {@code transfer.toBankCode()} and returns the {@link Acknowledgment}.
     *
     * @return the acknowledgement from the receiver bank, or {@code null} if
     *         no endpoint is configured for that bank.
     */
    public Acknowledgment sendCreditRequest(TransferResponse transfer) {
        String receiverBankCode = transfer.toBankCode();
        BankGrpcEndpointProperties.Endpoint endpoint =
                endpointProperties.getEndpoints().get(receiverBankCode);

        if (endpoint == null) {
            log.warn("No gRPC endpoint configured for receiver bank='{}'; " +
                     "skipping credit request for reference='{}'",
                     receiverBankCode, transfer.reference());
            return null;
        }

        ManagedChannel channel = channels.computeIfAbsent(receiverBankCode,
                k -> ManagedChannelBuilder
                        .forAddress(endpoint.getHost(), endpoint.getPort())
                        .usePlaintext()
                        .build());

        CentralBankGatewayGrpc.CentralBankGatewayBlockingStub stub =
                CentralBankGatewayGrpc.newBlockingStub(channel);

        CreditRequest request = buildCreditRequest(transfer);
        log.debug("Sending credit request to bank='{}' host='{}:{}' reference='{}'",
                receiverBankCode, endpoint.getHost(), endpoint.getPort(),
                transfer.reference());

        try {
            Acknowledgment ack = stub.requestCredit(request);
            log.info("Credit request acknowledged: bank='{}' ackId='{}' status='{}' reference='{}'",
                    receiverBankCode, ack.getAckId(), ack.getStatus(), transfer.reference());
            return ack;
        } catch (StatusRuntimeException e) {
            log.error("gRPC credit request to bank='{}' failed for reference='{}': status={}",
                    receiverBankCode, transfer.reference(), e.getStatus(), e);
            throw e;
        }
    }


    /**
     * Notifies the sender bank about the outcome of the credit instruction.
     * Calls {@code SendTransferNotification} on the sender bank's
     * {@link CentralBankGatewayGrpc} endpoint, passing the ack status returned
     * by the receiver bank so the sender knows whether the credit succeeded.
     *
     * @return the acknowledgement from the sender bank, or {@code null} if no
     *         endpoint is configured for that bank.
     */
    public Acknowledgment notifySenderBank(TransferResponse transfer, Acknowledgment receiverAck) {
        String senderBankCode = transfer.fromBankCode();
        BankGrpcEndpointProperties.Endpoint endpoint =
                endpointProperties.getEndpoints().get(senderBankCode);

        if (endpoint == null) {
            log.warn("No gRPC endpoint configured for sender bank='{}'; " +
                     "skipping notification for reference='{}'",
                     senderBankCode, transfer.reference());
            return null;
        }

        ManagedChannel channel = channels.computeIfAbsent(senderBankCode,
                k -> ManagedChannelBuilder
                        .forAddress(endpoint.getHost(), endpoint.getPort())
                        .usePlaintext()
                        .build());

        CentralBankGatewayGrpc.CentralBankGatewayBlockingStub stub =
                CentralBankGatewayGrpc.newBlockingStub(channel);

        com.centralbank.gateway.v1.TransferStatus status =
                (receiverAck != null && receiverAck.getStatus() == AckStatus.SUCCESS)
                        ? com.centralbank.gateway.v1.TransferStatus.CREDITED
                        : com.centralbank.gateway.v1.TransferStatus.FAILED;

        TransferNotification.Builder notifBuilder = TransferNotification.newBuilder()
                .setNotificationId(transfer.reference() + "-NOTIF")
                .setTransactionId(transfer.id().toString())
                .setEventTime(nowTimestamp())
                .setSenderBankCode(transfer.fromBankCode())
                .setReceiverBankCode(transfer.toBankCode())
                .setAmount(toMoney(transfer.amount(), transfer.currency()))
                .setStatus(status)
                .setReferenceNumber(transfer.reference());

        if (transfer.senderAccountNumber() != null) {
            notifBuilder.setSenderAccountNumber(transfer.senderAccountNumber());
        }
        if (transfer.beneficiaryAccountNumber() != null) {
            notifBuilder.setReceiverAccountNumber(transfer.beneficiaryAccountNumber());
        }

        log.debug("Sending transfer notification to sender bank='{}' host='{}:{}' reference='{}'",
                senderBankCode, endpoint.getHost(), endpoint.getPort(), transfer.reference());

        try {
            Acknowledgment ack = stub.sendTransferNotification(notifBuilder.build());
            log.info("Transfer notification acknowledged by sender bank='{}' ackId='{}' status='{}' reference='{}'",
                    senderBankCode, ack.getAckId(), ack.getStatus(), transfer.reference());
            return ack;
        } catch (StatusRuntimeException e) {
            log.error("gRPC notification to sender bank='{}' failed for reference='{}': status={}",
                    senderBankCode, transfer.reference(), e.getStatus(), e);
            throw e;
        }
    }


    private CreditRequest buildCreditRequest(TransferResponse transfer) {
        CreditRequest.Builder builder = CreditRequest.newBuilder()
                .setCreditRequestId(transfer.reference() + "-CR")
                .setTransactionId(transfer.id().toString())
                .setInstructionTime(nowTimestamp())
                .setSenderBankCode(transfer.fromBankCode())
                .setReceiverBankCode(transfer.toBankCode())
                .setAmount(toMoney(transfer.amount(), transfer.currency()))
                .setReferenceNumber(transfer.reference());

        if (transfer.senderAccountNumber() != null) {
            builder.setSenderAccountNumber(transfer.senderAccountNumber());
        }
        if (transfer.beneficiaryAccountNumber() != null) {
            builder.setBeneficiaryAccountNumber(transfer.beneficiaryAccountNumber());
        }

        return builder.build();
    }

    private static Money toMoney(BigDecimal amount, String currencyCode) {
        long units = amount.longValue();
        int nanos = amount.remainder(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(1_000_000_000L))
                .intValue();
        return Money.newBuilder()
                .setCurrencyCode(currencyCode)
                .setUnits(units)
                .setNanos(nanos)
                .build();
    }

    private static Timestamp nowTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

    @PreDestroy
    void shutdownChannels() {
        channels.forEach((bankCode, ch) -> {
            try {
                ch.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.debug("gRPC channel to bank='{}' shut down", bankCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ch.shutdownNow();
            }
        });
    }
}
