package com.settlement.rail.transfer;

/**
 * The rail's own state machine for its interaction with the <b>receiver</b>
 * bank on a {@link Transfer} — the credit leg, kept separate from
 * {@link TransferStatus} (the overall/debit-facing lifecycle visible to the
 * sender bank) so a crash mid-flight always leaves an unambiguous,
 * recoverable marker of exactly how far the credit request got:
 * <ul>
 *   <li>{@link #PENDING} — not yet sent to the receiver bank.</li>
 *   <li>{@link #REQUESTED} — the {@code RequestCredit} RPC is in flight;
 *       persisted <em>before</em> the blocking call so a crash during the
 *       call itself is distinguishable from one that never started.</li>
 *   <li>{@link #ACCEPTED} — the receiver bank acknowledged success (the
 *       beneficiary was credited).</li>
 *   <li>{@link #FAILED} — the receiver bank declined, or the call itself
 *       failed.</li>
 * </ul>
 */
public enum CreditLegStatus {
    PENDING,
    REQUESTED,
    ACCEPTED,
    FAILED
}
