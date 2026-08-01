package com.payment.transaction.entity;

/**
 * The receiver-bank-side (credit) state machine for an {@link IncomingCredit}
 * — the counterpart to {@link Payment.PaymentStatus} on the sender/debit
 * side. Every incoming credit request from the rail moves through these
 * states in order, each one persisted before the next step starts, so a
 * crash at any point leaves an unambiguous, recoverable marker instead of
 * an all-or-nothing in-memory operation:
 * <ul>
 *   <li>{@link #RECEIVED} — the request arrived and was durably recorded,
 *       before the beneficiary has been validated.</li>
 *   <li>{@link #ACCEPTED} — the beneficiary account was validated
 *       (active, exists); about to apply the credit.</li>
 *   <li>{@link #COMPLETED} — the credit was applied to the beneficiary's
 *       balance and the ledger. Terminal — a retried request with the same
 *       {@code creditRequestId} short-circuits to the same acknowledgment
 *       without re-crediting.</li>
 *   <li>{@link #FAILED} — validation failed or the credit could not be
 *       applied. Terminal.</li>
 * </ul>
 */
public enum CreditStatus {
    RECEIVED,
    ACCEPTED,
    COMPLETED,
    FAILED
}
