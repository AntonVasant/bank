package com.payment.transaction.service;

import com.centralbank.gateway.v1.AckStatus;
import com.centralbank.gateway.v1.AckType;
import com.centralbank.gateway.v1.Acknowledgment;
import com.centralbank.gateway.v1.CreditRequest;
import com.centralbank.gateway.v1.Money;
import com.google.protobuf.Timestamp;
import com.payment.transaction.entity.Account;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Orchestrates the incoming central-bank credit flow non-blocking.
 * <p>
 * All blocking DB work is dispatched on the {@code paymentExecutor} thread pool.
 * The gRPC I/O thread is released immediately — results are delivered via
 * {@link CompletableFuture} callbacks.
 *
 * <pre>
 *   ① paymentExecutor  – findAndValidateBeneficiary (read TX)
 *   ② paymentExecutor  – applyCredit (write TX)
 *   ③ (calling thread) – build Acknowledgment response
 * </pre>
 */
@Service
public class CreditOrchestrator {

    private final CreditService creditService;
    private final Executor executor;
    private final String localBankCode;

    public CreditOrchestrator(CreditService creditService,
                               @Qualifier("paymentExecutor") Executor executor,
                               @Value("${bank.id}") String localBankCode) {
        this.creditService = creditService;
        this.executor = executor;
        this.localBankCode = localBankCode;
    }
    
    public CompletableFuture<Acknowledgment> handleCreditRequest(CreditRequest request) {
        Instant receivedAt = Instant.now();
        BigDecimal amount = toDecimal(request.getAmount());

        // ① Load + validate beneficiary (read TX, paymentExecutor)
        return CompletableFuture
                .supplyAsync(() -> creditService.findAndValidateBeneficiary(
                        request.getBeneficiaryAccountNumber()), executor)

                // ② Apply credit (write TX, paymentExecutor)
                .thenApplyAsync((Account beneficiary) -> creditService.applyCredit(
                        request.getCreditRequestId(),
                        request.getTransactionId(),
                        beneficiary,
                        amount,
                        request.getSenderBankCode(),
                        request.getNarration()), executor)

                // ③ Build SUCCESS acknowledgment
                .thenApply(ackId -> buildAcknowledgment(
                        ackId, request, receivedAt, AckStatus.SUCCESS, null, null))

                // Compensation: return FAILURE acknowledgment instead of propagating exception
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    return buildAcknowledgment(
                            UUID.randomUUID().toString(),
                            request,
                            receivedAt,
                            AckStatus.FAILURE,
                            "CREDIT_FAILED",
                            cause.getMessage());
                });
    }
    
    private Acknowledgment buildAcknowledgment(String ackId,
                                                CreditRequest request,
                                                Instant receivedAt,
                                                AckStatus status,
                                                String errorCode,
                                                String errorMessage) {
        Instant processedAt = Instant.now();

        Acknowledgment.Builder builder = Acknowledgment.newBuilder()
                .setAckId(ackId)
                .setReferenceId(request.getCreditRequestId())
                .setAckType(AckType.CREDIT_REQUEST_ACK)
                .setStatus(status)
                .setReceivedAt(toProtoTimestamp(receivedAt))
                .setProcessedAt(toProtoTimestamp(processedAt))
                .setReceiverBankCode(localBankCode);

        if (errorCode != null)    builder.setErrorCode(errorCode);
        if (errorMessage != null) builder.setErrorMessage(errorMessage);

        return builder.build();
    }

    /** Converts a proto {@link Money} (units + nanos) to {@link BigDecimal}. */
    private static BigDecimal toDecimal(Money money) {
        return BigDecimal.valueOf(money.getUnits())
                .add(BigDecimal.valueOf(money.getNanos(), 9));
    }

    private static Timestamp toProtoTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
