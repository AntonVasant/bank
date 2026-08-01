package com.settlement.rail.transfer;

import java.util.List;

public interface TransferService {

    TransferResponse initiate(TransferRequest request);

    /**
     * Moves an ACCEPTED transfer to PROCESSING right before the async
     * credit/notify leg starts, so a mid-flight crash leaves an unambiguous,
     * recoverable marker in the DB instead of looking identical to a
     * transfer that was never picked up at all.
     */
    TransferResponse markProcessing(Long transferId);

    TransferResponse complete(Long transferId);

    /**
     * Drives a transfer to a terminal FAILED status with a reason, e.g. when
     * the receiver bank declines the credit or the gRPC call itself fails.
     * Idempotent: a no-op if the transfer is already COMPLETED or FAILED.
     */
    TransferResponse fail(Long transferId, String reason);

    /**
     * Marks the credit leg (this rail's interaction with the receiver bank)
     * {@code REQUESTED} — persisted right before the blocking
     * {@code RequestCredit} RPC, so a crash during that call is
     * distinguishable from a credit request that was never sent.
     */
    TransferResponse markCreditRequested(Long transferId);

    /** Marks the credit leg {@code ACCEPTED} — the receiver bank confirmed the beneficiary was credited. */
    TransferResponse markCreditAccepted(Long transferId);

    /** Marks the credit leg {@code FAILED} — the receiver bank declined, or the RPC itself failed. */
    TransferResponse markCreditFailed(Long transferId, String reason);

    /**
     * Finds transfers stuck in PROCESSING for longer than the stuck
     * threshold (e.g. the app crashed before the receiver bank's ack was
     * recorded) and marks them FAILED so nothing is left in an ambiguous,
     * unrecoverable state indefinitely.
     */
    void resetStuckTransfers();

    TransferResponse getById(Long id);

    TransferResponse getByUtr(String utr);

    List<TransferResponse> getAll();

    List<TransferResponse> getAllByStatus(TransferStatus status);

    List<TransferResponse> getAllByFromBank(Long fromBankId);

    List<TransferResponse> getAllByToBank(Long toBankId);
}
