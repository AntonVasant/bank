package com.payment.transaction.service;

import com.centralbank.gateway.v1.AckStatus;
import com.centralbank.gateway.v1.AckType;
import com.centralbank.gateway.v1.Acknowledgment;
import com.centralbank.gateway.v1.CreditRequest;
import com.centralbank.gateway.v1.Money;
import com.google.protobuf.Timestamp;
import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.CreditStatus;
import com.payment.transaction.entity.IncomingCredit;
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
 * <p>
 * Every request first durably records a {@link CreditStatus#RECEIVED}
 * checkpoint (idempotent on {@code creditRequestId}) before doing anything
 * else, so:
 * <ul>
 *   <li>a rail retry of a request we already {@code COMPLETED} (e.g. our
 *       first acknowledgment never made it back) short-circuits to the same
 *       outcome instead of crediting the beneficiary twice;</li>
 *   <li>a crash between recording the request and finishing it leaves an
 *       unambiguous, resumable marker ({@code RECEIVED} or {@code ACCEPTED})
 *       instead of looking identical to a request that was never picked up.</li>
 * </ul>
 * <pre>
 *   ① paymentExecutor  – recordReceived (write TX, idempotent)
 *   ② paymentExecutor  – findAndValidateBeneficiary (read TX)
 *   ③ paymentExecutor  – markAccepted (write TX)
 *   ④ paymentExecutor  – applyCredit (write TX)
 *   ⑤ (calling thread) – build Acknowledgment response
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

        // ① Durably record the request (write TX, paymentExecutor, idempotent on creditRequestId)
        return CompletableFuture
                .supplyAsync(() -> creditService.recordReceived(
                        request.getCreditRequestId(),
                        request.getTransactionId(),
                        request.getCycleId(),
                        request.getBeneficiaryAccountNumber(),
                        amount,
                        request.getAmount().getCurrencyCode(),
                        request.getSenderBankCode()), executor)
                .thenComposeAsync(incomingCredit ->
                        continueFromRecordedState(incomingCredit, request, receivedAt), executor);
    }

    /**
     * Branches on the durable state recorded by {@link CreditService#recordReceived}:
     * already-terminal requests ({@code COMPLETED}/{@code FAILED} — a rail
     * retry) short-circuit to the same outcome; anything else ({@code RECEIVED}
     * or {@code ACCEPTED} — first attempt, or a crash mid-flight on a
     * previous one) (re)validates and (re)applies the credit.
     */
    private CompletableFuture<Acknowledgment> continueFromRecordedState(IncomingCredit incomingCredit,
                                                                         CreditRequest request,
                                                                         Instant receivedAt) {
        if (incomingCredit.getStatus() == CreditStatus.COMPLETED) {
            return CompletableFuture.completedFuture(buildAcknowledgment(
                    incomingCredit.getCreditRequestId(), request, receivedAt, AckStatus.SUCCESS, null, null));
        }
        if (incomingCredit.getStatus() == CreditStatus.FAILED) {
            return CompletableFuture.completedFuture(buildAcknowledgment(
                    UUID.randomUUID().toString(), request, receivedAt,
                    AckStatus.FAILURE, "CREDIT_FAILED", incomingCredit.getFailureReason()));
        }

        Long incomingCreditId = incomingCredit.getId();

        // ② Load + validate beneficiary (read TX, paymentExecutor)
        return CompletableFuture
                .supplyAsync(() -> creditService.findAndValidateBeneficiary(
                        request.getBeneficiaryAccountNumber()), executor)

                // ③ Mark ACCEPTED (write TX, paymentExecutor)
                .thenApplyAsync((Account beneficiary) -> {
                    creditService.markAccepted(incomingCreditId);
                    return beneficiary;
                }, executor)

                // ④ Apply credit (write TX, paymentExecutor)
                .thenApplyAsync((Account beneficiary) -> creditService.applyCredit(
                        incomingCreditId, beneficiary, request.getNarration()), executor)

                // ⑤ Build SUCCESS acknowledgment
                .thenApply(ackId -> buildAcknowledgment(
                        ackId, request, receivedAt, AckStatus.SUCCESS, null, null))

                // Compensation: mark FAILED and return a FAILURE acknowledgment instead of propagating the exception
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    creditService.markFailed(incomingCreditId, cause.getMessage());
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
