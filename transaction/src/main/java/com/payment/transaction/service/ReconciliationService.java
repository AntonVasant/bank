package com.payment.transaction.service;

import com.payment.transaction.dto.ReconciliationRequest;
import com.payment.transaction.dto.ReconciliationResultRequest;
import com.payment.transaction.entity.SettlementAccount;
import com.payment.transaction.ledger.LedgerEntryLineRepository;
import com.payment.transaction.ledger.LedgerEntryType;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Independently recomputes this bank's own transaction count and net amount
 * for a settlement cycle directly from its own double-entry ledger
 * ({@code LedgerTransaction}/{@code LedgerEntryLine} against its
 * {@link SettlementAccount} — the same ledger that replaced TigerBeetle and
 * is the source of truth for every interbank payment this bank has made or
 * received) and compares them against the numbers the rail reported.
 * Reports the outcome back to the notification (delivery) service.
 */
@Slf4j
@Service
public class ReconciliationService {

    private final LedgerEntryLineRepository ledgerEntryLineRepository;
    private final RestClient.Builder restClientBuilder;
    private final String localBankCode;
    private final String deliveryBaseUrl;

    public ReconciliationService(LedgerEntryLineRepository ledgerEntryLineRepository,
                                  RestClient.Builder restClientBuilder,
                                  @Value("${bank.code}") String localBankCode,
                                  @Value("${delivery.rest.base-url}") String deliveryBaseUrl) {
        this.ledgerEntryLineRepository = ledgerEntryLineRepository;
        this.restClientBuilder = restClientBuilder;
        this.localBankCode = localBankCode;
        this.deliveryBaseUrl = deliveryBaseUrl;
    }

    @Timed("reconciliation.reconcile")
    @Transactional(readOnly = true)
    public void reconcile(ReconciliationRequest request) {
        Long settlementAccountId = SettlementAccount.SETTLEMENT_ACCOUNT_ID;
        long cycleId = parseCycleId(request.cycleId());

        // DEBIT entries against the settlement account = credits paid out to
        // our customers = money this bank received from other banks (matches
        // the rail's "toBank" / totalCredit side for this bank).
        BigDecimal incomingSum = ledgerEntryLineRepository.sumPostedAmount(
                cycleId, request.currency(), settlementAccountId, LedgerEntryType.DEBIT);
        // CREDIT entries against the settlement account = holds debited from
        // our customers = money this bank sent to other banks (matches the
        // rail's "fromBank" / totalDebit side for this bank).
        BigDecimal outgoingSum = ledgerEntryLineRepository.sumPostedAmount(
                cycleId, request.currency(), settlementAccountId, LedgerEntryType.CREDIT);
        long bankTransactionCount = ledgerEntryLineRepository.countPostedTransactions(
                cycleId, request.currency(), settlementAccountId);

        BigDecimal bankNetAmount = incomingSum.subtract(outgoingSum);
        BigDecimal railNetAmount = new BigDecimal(request.netAmount());

        boolean matched = bankTransactionCount == request.transactionCount()
                && bankNetAmount.compareTo(railNetAmount) == 0;

        log.info("Reconciliation cycleId='{}' bankCode='{}' currency='{}' rail(count={}, net={}) " +
                        "bank(count={}, net={}) matched={}",
                request.cycleId(), localBankCode, request.currency(),
                request.transactionCount(), railNetAmount, bankTransactionCount, bankNetAmount, matched);

        ReconciliationResultRequest result = new ReconciliationResultRequest(
                request.cycleId(),
                localBankCode,
                request.currency(),
                request.transactionCount(),
                request.netAmount(),
                (int) bankTransactionCount,
                bankNetAmount.toPlainString(),
                matched,
                Instant.now().toString());

        sendResult(result);
    }

    private void sendResult(ReconciliationResultRequest result) {
        try {
            restClientBuilder.build()
                    .post()
                    .uri(deliveryBaseUrl + "/api/reconciliation/result")
                    .body(result)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send reconciliation result cycleId='{}' bankCode='{}' currency='{}' to delivery",
                    result.cycleId(), result.bankCode(), result.currency(), e);
        }
    }

    /** Parses the rail's cycle id; 0 (matches nothing in the ledger) rather than guessing if it's missing/unparseable. */
    private long parseCycleId(String cycleId) {
        try {
            return Long.parseLong(cycleId);
        } catch (NumberFormatException e) {
            log.warn("Unparseable cycleId '{}' in reconciliation request; treating this bank's side as empty", cycleId);
            return 0L;
        }
    }
}
