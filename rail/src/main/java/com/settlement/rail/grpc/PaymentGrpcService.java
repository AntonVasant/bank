package com.settlement.rail.grpc;

import com.centralbank.gateway.v1.Acknowledgment;
import com.settlement.rail.transfer.TransferRequest;
import com.settlement.rail.transfer.TransferResponse;
import com.settlement.rail.transfer.TransferService;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@GrpcService
public class PaymentGrpcService implements PaymentServiceGrpc.AsyncService, BindableService {

    private final TransferService transferService;
    private final ReceiverBankGrpcClient receiverBankGrpcClient;
    private final Executor paymentExecutor;

    public PaymentGrpcService(TransferService transferService,
                               ReceiverBankGrpcClient receiverBankGrpcClient,
                               @Qualifier("paymentExecutor") Executor paymentExecutor) {
        this.transferService = transferService;
        this.receiverBankGrpcClient = receiverBankGrpcClient;
        this.paymentExecutor = paymentExecutor;
    }

    @Override
    public ServerServiceDefinition bindService() {
        return PaymentServiceGrpc.bindService(this);
    }

    /**
     * Server-streaming implementation of ProcessPayment.
     * <p>
     * <b>Response 1</b> (sent immediately after the DB commit):
     *   {@code acknowledged_by = RAIL} — the rail has reserved funds,
     *   written the ledger entries and committed the transaction.
     * <p>
     * <b>Response 2</b> (sent after the receiver bank confirms the credit):
     *   {@code acknowledged_by = RECEIVER_BANK} — the receiver bank has
     *   applied the credit and returned an acknowledgement.
     */
    @Override
    public void processPayment(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        try {
            TransferRequest transferRequest = new TransferRequest(
                    request.getFromBankCode(),
                    request.getToBankCode(),
                    new BigDecimal(request.getAmount()),
                    request.getCurrency(),
                    request.getReference(),
                    request.getDescription().isBlank() ? null : request.getDescription(),
                    request.getSenderAccountNumber().isBlank() ? null : request.getSenderAccountNumber(),
                    request.getBeneficiaryAccountNumber().isBlank() ? null : request.getBeneficiaryAccountNumber()
            );

            // Phase 1: DB commit — send Rail ack immediately
            TransferResponse transfer = transferService.initiate(transferRequest);
            responseObserver.onNext(toProto(transfer, AcknowledgedBy.RAIL));

            // Phase 2: async — credit receiver bank, then send its ack
            CompletableFuture.runAsync(() -> {
                try {
                    Acknowledgment receiverAck = receiverBankGrpcClient.sendCreditRequest(transfer);
                    receiverBankGrpcClient.notifySenderBank(transfer, receiverAck);
                    TransferResponse completedTransfer = transferService.complete(transfer.id());
                    responseObserver.onNext(toProto(completedTransfer, AcknowledgedBy.RECEIVER_BANK));
                    responseObserver.onCompleted();
                } catch (Exception ex) {
                    log.error("Async credit/notify failed for reference={}", transfer.reference(), ex);
                    responseObserver.onError(
                            Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
                }
            }, paymentExecutor);

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPayment(GetPaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        try {
            TransferResponse transfer = transferService.getByReference(request.getReference());
            responseObserver.onNext(toProto(transfer, AcknowledgedBy.ACKNOWLEDGED_BY_UNSPECIFIED));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private PaymentResponse toProto(TransferResponse t, AcknowledgedBy acknowledgedBy) {
        PaymentResponse.Builder builder = PaymentResponse.newBuilder()
                .setId(t.id())
                .setReference(t.reference())
                .setFromBankId(t.fromBankId())
                .setFromBankCode(t.fromBankCode())
                .setToBankId(t.toBankId())
                .setToBankCode(t.toBankCode())
                .setAmount(t.amount().toPlainString())
                .setCurrency(t.currency())
                .setStatus(t.status().name())
                .setAcknowledgedBy(acknowledgedBy);

        if (t.description() != null)   builder.setDescription(t.description());
        if (t.failureReason() != null) builder.setFailureReason(t.failureReason());
        if (t.createdAt() != null)     builder.setCreatedAt(t.createdAt().toString());
        if (t.updatedAt() != null)     builder.setUpdatedAt(t.updatedAt().toString());
        if (t.completedAt() != null)   builder.setCompletedAt(t.completedAt().toString());

        return builder.build();
    }
}

