package com.settlement.rail.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for the credit/notify leg in {@link PaymentOrchestrator}: if the
 * app crashes (or the executor thread dies) after a transfer is marked
 * PROCESSING but before it reaches a terminal status, this leaves the row
 * stuck with no further code path ever revisiting it. This scheduler finds
 * those stuck transfers and marks them FAILED so every transfer always ends
 * up in a definite, recoverable state — mirroring the same
 * stuck-in-PROCESSING pattern already used for {@code OutboxEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferReconciliationScheduler {

    private final TransferService transferService;

    /**
     * Finds transfers stuck in PROCESSING for more than the stuck threshold
     * and marks them FAILED for manual reconciliation.
     *
     * Runs 60 seconds after the previous execution completes.
     */
    @Scheduled(fixedDelay = 60_000)
    public void resetStuckTransfers() {
        log.debug("Checking for transfers stuck in PROCESSING");
        transferService.resetStuckTransfers();
    }
}
