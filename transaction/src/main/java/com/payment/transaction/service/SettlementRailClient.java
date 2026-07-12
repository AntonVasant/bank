package com.payment.transaction.service;

import com.payment.transaction.dto.SettlementRailRequest;
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
     * The returned future completes with the settlement reference on success,
     * or exceptionally with a {@link ResponseStatusException} (502) on rejection or unavailability.
     */
    public CompletableFuture<String> submit(SettlementRailRequest request) {
        PaymentRequest grpcRequest = PaymentRequest.newBuilder()
                .setFromBankCode(request.sourceBankCode())
                .setToBankCode(request.destinationBankCode())
                .setAmount(request.amount().toPlainString())
                .setCurrency(request.currency())
                .setReceiverAccountNumber(request.destinationAccountNumber())
                .setSenderAccountNumber(request.sourceAccountNumber())
                .setReference(request.paymentReference())
                .setDescription("Transfer to account " + request.destinationAccountNumber())
                .build();

        CompletableFuture<String> result = new CompletableFuture<>();

        stub.processPayment(grpcRequest, new StreamObserver<PaymentResponse>() {
            private PaymentResponse response;

            @Override
            public void onNext(PaymentResponse value) {
                this.response = value;
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Settlement rail unavailable for payment " + request.paymentReference()
                        + ": " + t.getMessage(), t));
            }

            @Override
            public void onCompleted() {
                if (!"ACCEPTED".equalsIgnoreCase(response.getStatus())) {
                    result.completeExceptionally(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Settlement rail rejected payment " + request.paymentReference()
                            + ": " + response.getFailureReason()));
                } else {
                    result.complete(response.getReference());
                }
            }
        });

        return result;
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
