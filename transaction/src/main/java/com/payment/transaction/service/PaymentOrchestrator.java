package com.payment.transaction.service;

import com.payment.transaction.dto.CreatePaymentRequest;
import com.payment.transaction.dto.PaymentResponse;
import com.payment.transaction.dto.SettlementRailRequest;
import com.payment.transaction.entity.Account;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
@Service
public class PaymentOrchestrator {

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

    // ── Entry point ──────────────────────────────────────────────────────────

    public CompletableFuture<PaymentResponse> createPayment(String idempotencyKey,
                                                             CreatePaymentRequest request) {
        // Fast-path: idempotency check on the calling thread — cheap read, no executor needed
//        var cached = paymentService.checkIdempotency(idempotencyKey);
//        if (cached.isPresent()) {
//            return CompletableFuture.completedFuture(cached.get());
//        }

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
        // Step 1 (paymentExecutor): debit + persist PROCESSING payment + ledger — commits before gRPC
        return CompletableFuture
                .supplyAsync(() -> paymentService.prepareInterBankPayment(idempotencyKey, request, source),
                        executor)
                .thenCompose(savedPayment -> {
                    SettlementRailRequest railRequest = new SettlementRailRequest(
                            savedPayment.getPaymentReference(),
                            localBankCode,
                            request.destinationAccountNumber(),
                            request.destinationBankCode(),
                            savedPayment.getAmount(),
                            source.getCurrency().name(),
                            request.sourceAccountNumber()
                    );

                    // Step 2: non-blocking gRPC call (gRPC executor thread)
                    return settlementRailClient.submit(railRequest)

                            // Step 3 success (paymentExecutor): mark COMPLETED + post ledger + outbox
                            .thenApplyAsync(
                                    settlementRef -> paymentService.completeInterBankPayment(
                                            idempotencyKey, savedPayment.getId(), settlementRef),
                                    executor)

                            // Step 3 failure (paymentExecutor): refund debit + mark FAILED
                            .exceptionallyCompose(ex -> CompletableFuture.supplyAsync(() -> {
                                paymentService.refundInterBankPayment(
                                        savedPayment.getId(),
                                        source.getId(),
                                        savedPayment.getAmount());
                                throw ex instanceof CompletionException
                                        ? (CompletionException) ex
                                        : new CompletionException(ex);
                            }, executor));
                });
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
}
