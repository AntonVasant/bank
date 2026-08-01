package com.payment.transaction.service;

/**
 * Carried by {@link SettlementFutures#cbAcknowledged()} once the rail sends
 * its first ({@code RAIL}) acknowledgement — the settlement reference, the
 * settlement cycle id the transaction was recorded against (or, on
 * rejection, the real currently-open cycle id to retry with), and the
 * transfer status ({@code "ACCEPTED"} or {@code "FAILED"} — e.g.
 * {@code CYCLE_MISMATCH}, meaning our cached cycle id had gone stale).
 */
public record CbAcknowledgement(String reference, String cycleId, String status) {

    public boolean isCycleMismatch() {
        return "FAILED".equals(status);
    }
}
