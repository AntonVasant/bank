package com.payment.transaction.service;

import com.payment.transaction.dto.SettlementRailRequest;
import com.settlement.rail.grpc.AcknowledgedBy;
import com.settlement.rail.grpc.PaymentRequest;
import com.settlement.rail.grpc.PaymentResponse;
import com.settlement.rail.grpc.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SettlementRailClient {

    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceStub stub;

    public SettlementRailClient(
            @Value("${settlement.rail.grpc.host}") String host,
            @Value("${settlement.rail.grpc.port}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = PaymentServiceGrpc.newStub(channel);
    }

    /**
     * Sends a payment instruction to the settlement rail via gRPC asynchronously.
     * <p>
     * The settlement rail streams two responses back:
     * <ol>
     *   <li>status {@code "ACCEPTED"}   — central bank has acknowledged the payment.</li>
     *   <li>status {@code "COMPLETED"}  — payment has been fully settled.</li>
     * </ol>
     * Both futures complete exceptionally with a {@link ResponseStatusException} (502)
     * if the rail rejects the payment or the connection fails.
     *
     * @return {@link SettlementFutures} carrying one future per stage
     */
    public SettlementFutures submit(SettlementRailRequest request) {
        PaymentRequest grpcRequest = PaymentRequest.newBuilder()
                .setFromBankCode(request.sourceBankCode())
                .setToBankCode(request.destinationBankCode())
                .setAmount(request.amount().toPlainString())
                .setCurrency(request.currency())
                .setBeneficiaryAccountNumber(request.destinationAccountNumber())
                .setSenderAccountNumber(request.sourceAccountNumber())
                .setUtr(request.uetr())
                .setDescription("Transfer to account " + request.destinationAccountNumber())
                .setCycleId(String.valueOf(request.cycleId()))
                .build();

        CompletableFuture<CbAcknowledgement> cbAcknowledged = new CompletableFuture<>();
        CompletableFuture<String> completed = new CompletableFuture<>();

        stub.processPayment(grpcRequest, new StreamObserver<PaymentResponse>() {

            @Override
            public void onNext(PaymentResponse value) {
                AcknowledgedBy ack = value.getAcknowledgedBy();
                if (ack == AcknowledgedBy.RAIL) {
                    // Response 1: rail has committed the transfer (or rejected
                    // it, e.g. cycle mismatch — status carries which).
                    cbAcknowledged.complete(new CbAcknowledgement(value.getUtr(), value.getCycleId(), value.getStatus()));
                } else if (ack == AcknowledgedBy.RECEIVER_BANK) {
                    // Response 2: receiver bank has confirmed the credit
                    completed.complete(value.getUtr());
                } else {
                    // Unspecified acknowledged_by — treat as failure
                    ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Settlement rail returned unspecified acknowledgement for payment "
                            + request.paymentReference()
                            + ": " + value.getFailureReason());
                    cbAcknowledged.completeExceptionally(ex);
                    completed.completeExceptionally(ex);
                }
            }

            @Override
            public void onError(Throwable t) {
                ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Settlement rail unavailable for payment " + request.paymentReference()
                        + ": " + t.getMessage(), t);
                cbAcknowledged.completeExceptionally(ex);
                completed.completeExceptionally(ex);
            }

            @Override
            public void onCompleted() {
                // Safety net: if the stream closed before we got a COMPLETED response
                // completeExceptionally is a no-op on an already-completed future
                completed.completeExceptionally(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Settlement rail closed stream without a COMPLETED response for payment "
                        + request.paymentReference()));
            }
        });

        return new SettlementFutures(cbAcknowledged, completed);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
