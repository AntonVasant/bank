package com.settlement.rail.grpc;

import com.settlement.rail.transfer.PaymentOrchestrator;
import com.settlement.rail.transfer.TransferRequest;
import com.settlement.rail.transfer.TransferResponse;
import com.settlement.rail.transfer.TransferService;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcService implements PaymentServiceGrpc.AsyncService, BindableService {

    private final PaymentOrchestrator paymentOrchestrator;
    @Override
    public ServerServiceDefinition bindService() {
        return PaymentServiceGrpc.bindService(this);
    }

    private final TransferService transferService;

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

            TransferResponse transfer = paymentOrchestrator.initiate(transferRequest);
            responseObserver.onNext(toProto(transfer));
             responseObserver.onCompleted();
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
            responseObserver.onNext(toProto(transfer));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private PaymentResponse toProto(TransferResponse t) {
        PaymentResponse.Builder builder = PaymentResponse.newBuilder()
                .setId(t.id())
                .setReference(t.reference())
                .setFromBankId(t.fromBankId())
                .setFromBankCode(t.fromBankCode())
                .setToBankId(t.toBankId())
                .setToBankCode(t.toBankCode())
                .setAmount(t.amount().toPlainString())
                .setCurrency(t.currency())
                .setStatus(t.status().name());

        if (t.description() != null)    builder.setDescription(t.description());
        if (t.failureReason() != null)  builder.setFailureReason(t.failureReason());
        if (t.createdAt() != null)      builder.setCreatedAt(t.createdAt().toString());
        if (t.updatedAt() != null)      builder.setUpdatedAt(t.updatedAt().toString());
        if (t.completedAt() != null)    builder.setCompletedAt(t.completedAt().toString());

        return builder.build();
    }
}
