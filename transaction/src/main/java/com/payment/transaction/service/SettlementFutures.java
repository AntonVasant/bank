package com.payment.transaction.service;

import java.util.concurrent.CompletableFuture;

/**
 * Carries the two asynchronous results produced by a single server-streaming
 * {@code processPayment} gRPC call to the settlement rail.
 *
 * <pre>
 *   cbAcknowledged – completes (with the CB reference) when the rail sends
 *                    its first response: status = "ACCEPTED".
 *   completed      – completes (with the final settlement reference) when the
 *                    rail sends its second response: status = "COMPLETED".
 * </pre>
 *
 * Both futures complete exceptionally if the stream errors or the rail rejects
 * the payment at either stage.
 */
public record SettlementFutures(
        CompletableFuture<String> cbAcknowledged,
        CompletableFuture<String> completed) {
}
