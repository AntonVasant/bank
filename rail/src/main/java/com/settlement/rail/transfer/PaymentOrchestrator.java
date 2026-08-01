package com.settlement.rail.transfer;

import com.centralbank.gateway.v1.Acknowledgment;
import com.centralbank.gateway.v1.AckStatus;
import com.settlement.rail.grpc.ReceiverBankGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Coordinates a payment end-to-end.
 *
 * Phase 1 – DB work (delegated to {@link TransferService#initiate}):
 *   reserve validation, transfer record, ledger entries, reserve updates,
 *   and outbox event — all committed in a single transaction.
 *
 * Phase 2 – post-commit async work (non-blocking, via {@link CompletableFuture}):
 *   any network calls (notifications, webhooks, etc.) are fired after the
 *   transaction is fully committed and never block the caller thread, and
 *   {@link #creditReceiverAndFinalize} always drives the transfer to a
 *   terminal DB status (COMPLETED or FAILED) so it's never left ambiguous.
 *
 * This is the single place that owns the credit/notify/finalize sequence —
 * both the REST entry point ({@link #initiate}) and the gRPC entry point
 * ({@code PaymentGrpcService}) call {@link #creditReceiverAndFinalize} so
 * transfer status handling can never drift out of sync between the two.
 */
@Slf4j
@Component
public class PaymentOrchestrator {

    private final TransferService transferService;
    private final Executor paymentExecutor;
    private final ReceiverBankGrpcClient receiverBankGrpcClient;

    public PaymentOrchestrator(TransferService transferService,
                               @Qualifier("paymentExecutor") Executor paymentExecutor,
                               ReceiverBankGrpcClient receiverBankGrpcClient) {
        this.transferService = transferService;
        this.paymentExecutor = paymentExecutor;
        this.receiverBankGrpcClient = receiverBankGrpcClient;
    }

    public TransferResponse initiate(TransferRequest request) {
        TransferResponse response = transferService.initiate(request);

        CompletableFuture.runAsync(() -> creditReceiverAndFinalize(response), paymentExecutor)
                .exceptionally(ex -> {
                    // creditReceiverAndFinalize already catches its own
                    // failures and marks the transfer FAILED; this only
                    // fires if something outside that (e.g. the executor
                    // itself, or fail() hitting a DB error) blows up.
                    log.error("Post-commit async task failed unexpectedly for utr={}", response.utr(), ex);
                    return null;
                });

        return response;
    }

    /**
     * Runs after the DB transaction has committed.
     * <ol>
     *   <li>Marks the transfer PROCESSING.</li>
     *   <li>Marks the credit leg REQUESTED, then sends a {@code RequestCredit}
     *       gRPC call to the receiver bank and captures its
     *       {@link Acknowledgment} — marking the credit leg ACCEPTED or
     *       FAILED as soon as the outcome is known, before doing anything
     *       else, so a crash right after this call always leaves the credit
     *       leg's own state unambiguous regardless of what happens next.</li>
     *   <li>Forwards the result to the sender bank via
     *       {@code SendTransferNotification}.</li>
     *   <li>Drives the transfer to COMPLETED (receiver ack = SUCCESS) or
     *       FAILED (receiver ack missing/unsuccessful, or any exception
     *       along the way) — always a terminal status, always persisted,
     *       so the DB stays consistent and recoverable no matter what
     *       happens on the network leg.</li>
     * </ol>
     */
    public TransferResponse creditReceiverAndFinalize(TransferResponse response) {
        transferService.markProcessing(response.id());
        transferService.markCreditRequested(response.id());
        try {
            log.debug("Sending credit request to receiver bank for transfer utr={} id={}",
                    response.utr(), response.id());
            Acknowledgment receiverAck = receiverBankGrpcClient.sendCreditRequest(response);

            boolean receiverSucceeded = receiverAck != null && receiverAck.getStatus() == AckStatus.SUCCESS;
            String reason = receiverAck == null
                    ? "No gRPC endpoint configured for receiver bank"
                    : "Receiver bank declined the credit: ack status=" + receiverAck.getStatus();

            if (receiverSucceeded) {
                transferService.markCreditAccepted(response.id());
            } else {
                transferService.markCreditFailed(response.id(), reason);
            }

            receiverBankGrpcClient.notifySenderBank(response, receiverAck);

            if (receiverSucceeded) {
                return transferService.complete(response.id());
            }

            log.warn("Transfer utr={} id={} failed: {}", response.utr(), response.id(), reason);
            return transferService.fail(response.id(), reason);
        } catch (Exception e) {
            log.error("Credit/notify failed for transfer utr={} id={}",
                    response.utr(), response.id(), e);
            transferService.markCreditFailed(response.id(), e.getMessage());
            return transferService.fail(response.id(), e.getMessage());
        }
    }
}

