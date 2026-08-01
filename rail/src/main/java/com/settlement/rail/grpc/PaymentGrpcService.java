package com.settlement.rail.grpc;

import com.settlement.rail.transfer.PaymentOrchestrator;
import com.settlement.rail.transfer.TransferRequest;
import com.settlement.rail.transfer.TransferResponse;
import com.settlement.rail.transfer.TransferService;
import com.settlement.rail.transfer.TransferStatus;
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
    private final PaymentOrchestrator paymentOrchestrator;
    private final Executor paymentExecutor;

    public PaymentGrpcService(TransferService transferService,
                               PaymentOrchestrator paymentOrchestrator,
                               @Qualifier("paymentExecutor") Executor paymentExecutor) {
        this.transferService = transferService;
        this.paymentOrchestrator = paymentOrchestrator;
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
     *   applied the credit and returned an acknowledgement. The transfer's
     *   {@code status} in this response reflects the real outcome
     *   (COMPLETED or FAILED) since {@link PaymentOrchestrator#creditReceiverAndFinalize}
     *   always drives it to a terminal, persisted state before this
     *   response is sent — never left dangling regardless of what happens
     *   on the network leg.
     */
    @Override
    public void processPayment(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        try {
            TransferRequest transferRequest = new TransferRequest(
                    request.getFromBankCode(),
                    request.getToBankCode(),
                    new BigDecimal(request.getAmount()),
                    request.getCurrency(),
                    request.getUtr(),
                    request.getDescription().isBlank() ? null : request.getDescription(),
                    request.getSenderAccountNumber().isBlank() ? null : request.getSenderAccountNumber(),
                    request.getBeneficiaryAccountNumber().isBlank() ? null : request.getBeneficiaryAccountNumber(),
                    request.getCycleId().isBlank() ? null : Long.valueOf(request.getCycleId())
            );

            // Phase 1: DB commit — send Rail ack immediately
            TransferResponse transfer = transferService.initiate(transferRequest);
            responseObserver.onNext(toProto(transfer, AcknowledgedBy.RAIL));

            if (transfer.status() == TransferStatus.FAILED) {
                // Rejected before touching the DB (e.g. cycle mismatch) —
                // there's no real transfer to finalize, so this is the only
                // response; PaymentResponse.cycle_id carries the real open
                // cycle id for the sender to retry with.
                responseObserver.onCompleted();
                return;
            }

            // Phase 2: async — credit receiver bank, notify sender, and
            // finalize the transfer's status (shared with the REST path via
            // PaymentOrchestrator so both entry points behave identically).
            CompletableFuture.runAsync(() -> {
                TransferResponse finalTransfer = paymentOrchestrator.creditReceiverAndFinalize(transfer);
                responseObserver.onNext(toProto(finalTransfer, AcknowledgedBy.RECEIVER_BANK));
                responseObserver.onCompleted();
            }, paymentExecutor).exceptionally(ex -> {
                // creditReceiverAndFinalize already catches its own failures
                // and persists FAILED; this only fires for something outside
                // that (e.g. streaming the response itself failing).
                log.error("Unexpected failure finalizing transfer utr={}", transfer.utr(), ex);
                responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
                return null;
            });

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }


    @Override
    public void getPayment(GetPaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        try {
            TransferResponse transfer = transferService.getByUtr(request.getUtr());
            responseObserver.onNext(toProto(transfer, AcknowledgedBy.ACKNOWLEDGED_BY_UNSPECIFIED));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private PaymentResponse toProto(TransferResponse t, AcknowledgedBy acknowledgedBy) {
        PaymentResponse.Builder builder = PaymentResponse.newBuilder()
                .setId(t.id())
                .setUtr(t.utr())
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
        if (t.cycleId() != null)       builder.setCycleId(t.cycleId());

        return builder.build();
    }
}

