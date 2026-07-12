package com.settlement.rail.transfer;

import com.centralbank.gateway.v1.Acknowledgment;
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
 *   transaction is fully committed and never block the caller thread.
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

        CompletableFuture.runAsync(() -> creditReceiverAndNotifySender(response), paymentExecutor)
                .exceptionally(ex -> {
                    log.error("Post-commit async task failed for reference={}", response.reference(), ex);
                    return null;
                });

        return response;
    }

    /**
     * Runs after the DB transaction has committed.
     * <ol>
     *   <li>Sends a {@code RequestCredit} gRPC call to the receiver bank and
     *       captures its {@link Acknowledgment}.</li>
     *   <li>Forwards the result to the sender bank via
     *       {@code SendTransferNotification} so the sender knows whether the
     *       credit was applied successfully.</li>
     * </ol>
     */
    private void creditReceiverAndNotifySender(TransferResponse response) {
        log.debug("Sending credit request to receiver bank for transfer reference={} id={}",
                response.reference(), response.id());
        Acknowledgment receiverAck = receiverBankGrpcClient.sendCreditRequest(response);
        receiverBankGrpcClient.notifySenderBank(response, receiverAck);
    }
}
