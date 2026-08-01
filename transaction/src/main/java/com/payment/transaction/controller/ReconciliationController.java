package com.payment.transaction.controller;

import com.payment.transaction.dto.ReconciliationRequest;
import com.payment.transaction.service.ReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executor;

/**
 * Receives the notification (delivery) service's reconciliation request for
 * a settlement cycle. Accepts immediately (202) — the actual comparison
 * against this bank's own records happens asynchronously and is reported
 * back to delivery via its own {@code POST /api/reconciliation/result} call.
 */
@Slf4j
@RestController
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final Executor executor;

    public ReconciliationController(ReconciliationService reconciliationService,
                                     @Qualifier("paymentExecutor") Executor executor) {
        this.reconciliationService = reconciliationService;
        this.executor = executor;
    }

    @PostMapping("/api/reconciliation")
    public ResponseEntity<Void> receiveReconciliationRequest(@RequestBody ReconciliationRequest request) {
        log.info("Reconciliation request received cycleId='{}' bankCode='{}' currency='{}' transactionCount={} netAmount={}",
                request.cycleId(), request.bankCode(), request.currency(), request.transactionCount(), request.netAmount());

        executor.execute(() -> {
            try {
                reconciliationService.reconcile(request);
            } catch (Exception e) {
                log.error("Reconciliation failed for cycleId='{}' bankCode='{}' currency='{}'",
                        request.cycleId(), request.bankCode(), request.currency(), e);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
