package com.payment.transaction.service;

import com.payment.transaction.dto.CreatePaymentRequest;
import com.payment.transaction.dto.PaymentResponse;
import com.payment.transaction.dto.SettlementRailRequest;
import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Orchestrates the full payment lifecycle.
 * <p>
 * All DB work is delegated to {@link PaymentService} ({@code @Transactional} methods)
 * and executed on the {@code paymentExecutor} thread pool. Network calls
 * ({@link SettlementRailClient}) are non-blocking and run on the gRPC executor;
 * their callbacks are returned to the {@code paymentExecutor} for the follow-up
 * DB writes, keeping gRPC threads free.
 */
@Slf4j
@Service
public class PaymentOrchestrator {

    /** Cap on cycle-mismatch retries so a persistently-misbehaving rail can't loop a payment forever. */
    private static final int MAX_CYCLE_RETRY_ATTEMPTS = 3;

    private final PaymentService paymentService;
    private final SettlementRailClient settlementRailClient;
    private final Executor executor;
    private final String localBankCode;

    public PaymentOrchestrator(PaymentService paymentService,
                                SettlementRailClient settlementRailClient,
                                @Qualifier("paymentExecutor") Executor executor,
                                @Value("${bank.code}") String localBankCode) {
        this.paymentService = paymentService;
        this.settlementRailClient = settlementRailClient;
        this.executor = executor;
        this.localBankCode = localBankCode;
    }
    
    public CompletableFuture<PaymentResponse> createPayment(String idempotencyKey,
                                                             CreatePaymentRequest request) {
        var cached = paymentService.checkIdempotency(idempotencyKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        validateRequest(request);

        boolean interBank = request.destinationBankCode() != null
                && !request.destinationBankCode().isBlank();

        // Dispatch all DB + network work to paymentExecutor
        return CompletableFuture
                .supplyAsync(() -> {
                    paymentService.registerIdempotencyKey(idempotencyKey);
                    return paymentService.loadAndValidateSource(request);
                }, executor)
                .thenCompose(source -> interBank
                        ? executeInterBank(idempotencyKey, request, source)
                        : CompletableFuture.supplyAsync(
                                () -> paymentService.executeIntraBank(idempotencyKey, request, source),
                                executor));
    }

    public PaymentResponse getPayment(Long paymentId) {
        return paymentService.getPayment(paymentId);
    }


    private CompletableFuture<PaymentResponse> executeInterBank(String idempotencyKey,
                                                                  CreatePaymentRequest request,
                                                                  Account source) {
        // Step 1 (paymentExecutor): read the cached cycle id from Redis, then
        // place the hold + persist PROCESSING payment — commits before gRPC.
        // The Redis read happens before prepareInterBankPayment's DB
        // transaction opens, so it never holds a DB connection open.
        // Moment 1: the caller gets a response the instant this step finishes;
        // it never waits for the rail / receiver-bank round trip below.
        return CompletableFuture
                .supplyAsync(() -> {
                    long cycleId = paymentService.currentCycleId();
                    return paymentService.prepareInterBankPayment(idempotencyKey, request, source, cycleId);
                }, executor)
                .thenApply(savedPayment -> {
                    continueSettlement(idempotencyKey, request, source, savedPayment);
                    return paymentService.toProcessingResponseAndMarkIdempotent(idempotencyKey, savedPayment);
                });
    }

    /**
     * Runs fully detached from the response already returned to the caller:
     * calls the rail, waits for the receiver bank's outcome, then either
     * posts the hold (Moment 3 — success) or voids it (failure). The final
     * status is only ever observable via {@link #getPayment}.
     * <p>
     * The CB acknowledgement (first stream response) carries the real
     * settlement cycle id — {@code completed} is only awaited <em>after</em>
     * it resolves, so the cycle id the payment is tagged with is always the
     * one actually returned by the central bank, never guessed locally.
     * <p>
     * Rejection path (cycle mismatch): our cached cycle id had gone stale by
     * the time this reached the rail. The rail returns the real, currently
     * open cycle id — re-cache it and retag the payment with it (the ledger
     * hold itself is untouched), and retry with the same UTR (bounded by
     * {@link #MAX_CYCLE_RETRY_ATTEMPTS}).
     */
    private void continueSettlement(String idempotencyKey,
                                     CreatePaymentRequest request,
                                     Account source,
                                     Payment savedPayment) {
        continueSettlement(idempotencyKey, request, source, savedPayment, 1);
    }

    private void continueSettlement(String idempotencyKey,
                                     CreatePaymentRequest request,
                                     Account source,
                                     Payment savedPayment,
                                     int attempt) {
        long cycleId = parseCycleId(savedPayment.getCycleId());
        SettlementRailRequest railRequest = new SettlementRailRequest(
                savedPayment.getPaymentReference(),
                localBankCode,
                request.destinationAccountNumber(),
                request.destinationBankCode(),
                savedPayment.getAmount(),
                source.getCurrency().name(),
                request.sourceAccountNumber(),
                cycleId
        );

        SettlementFutures futures = settlementRailClient.submit(railRequest);

        futures.cbAcknowledged()
                .thenComposeAsync(cbAck -> {
                    if (cbAck.isCycleMismatch()) {
                        return handleCycleMismatch(idempotencyKey, request, source, savedPayment, cbAck, attempt);
                    }

                    paymentService.markCbAcknowledged(savedPayment.getId(), cbAck.cycleId());

                    return futures.completed().thenAcceptAsync(
                            settlementRef -> paymentService.completeInterBankPayment(
                                    idempotencyKey, savedPayment.getId(), settlementRef, parseCycleId(cbAck.cycleId())),
                            executor);
                }, executor)

                // Moment 2 (invalid account) / CB never acknowledged / any
                // round-trip failure: void the hold, never a "refund" — no
                // funds ever moved.
                .exceptionallyCompose(ex -> CompletableFuture.runAsync(() -> {
                    log.warn("Voiding hold for payment {} after round-trip failure: {}",
                            savedPayment.getId(), ex.getMessage());
                    paymentService.refundInterBankPayment(
                            savedPayment.getId(),
                            source.getId(),
                            savedPayment.getAmount());
                }, executor))
                .exceptionally(ex -> {
                    log.error("Unrecoverable failure finalizing payment {}: {}",
                            savedPayment.getId(), ex.getMessage(), ex);
                    return null;
                });
    }

    /**
     * The rail rejected because our cached cycle id had gone stale — void
     * the stale hold, re-hold tagged with the real cycle id the rail
     * returned, and retry with the same UTR (step 16-20 of the cycle
     * broadcast design).
     */
    private CompletableFuture<Void> handleCycleMismatch(String idempotencyKey,
                                                         CreatePaymentRequest request,
                                                         Account source,
                                                         Payment savedPayment,
                                                         CbAcknowledgement cbAck,
                                                         int attempt) {
        if (attempt >= MAX_CYCLE_RETRY_ATTEMPTS) {
            log.error("Payment {} exceeded {} cycle-mismatch retries; voiding the hold and giving up",
                    savedPayment.getId(), MAX_CYCLE_RETRY_ATTEMPTS);
            return CompletableFuture.runAsync(() -> paymentService.refundInterBankPayment(
                    savedPayment.getId(), source.getId(), savedPayment.getAmount()), executor);
        }

        long newCycleId = parseCycleId(cbAck.cycleId());
        log.warn("Payment {} rejected (cycle mismatch), retrying (attempt {}/{}) with cycleId={}",
                savedPayment.getId(), attempt + 1, MAX_CYCLE_RETRY_ATTEMPTS, newCycleId);

        return CompletableFuture.supplyAsync(
                        () -> paymentService.reholdForNewCycle(savedPayment.getId(), newCycleId), executor)
                .thenAccept(updatedPayment ->
                        continueSettlement(idempotencyKey, request, source, updatedPayment, attempt + 1));
    }



    private void validateRequest(CreatePaymentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        boolean interBank = request.destinationBankCode() != null
                && !request.destinationBankCode().isBlank();
        if (interBank) {
            if (request.destinationAccountNumber() == null || request.destinationAccountNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "destinationAccountNumber is required for inter-bank payments");
            }
        }
    }

    /** Parses the cycle id the central bank actually returned; 0 (untagged) if it's missing/unparseable rather than guessing a value. */
    private static long parseCycleId(String cycleId) {
        if (cycleId == null || cycleId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(cycleId);
        } catch (NumberFormatException e) {
            log.warn("Central bank returned non-numeric cycle id '{}'; leaving ledger posting untagged", cycleId);
            return 0L;
        }
    }
}
