package com.payment.transaction.service;

import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.OutboxEvent;
import com.payment.transaction.repository.AccountRepository;
import com.payment.transaction.repository.OutboxEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pure DB layer for incoming central-bank credit operations.
 * Every public method runs inside its own {@link Transactional} boundary.
 * No network calls, no {@link java.util.concurrent.CompletableFuture} — those live in {@link CreditOrchestrator}.
 */
@Service
public class CreditService {

    private static final String CREDIT_TOPIC = "credits.applied";

    private final AccountRepository accountRepository;
    private final OutboxEventRepository outboxEventRepository;

    public CreditService(AccountRepository accountRepository,
                         OutboxEventRepository outboxEventRepository) {
        this.accountRepository = accountRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    // ── Account lookup ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Account findAndValidateBeneficiary(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Beneficiary account not found: " + accountNumber));
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Beneficiary account is not active: " + accountNumber);
        }
        return account;
    }

    // ── Credit application ────────────────────────────────────────────────────

    /**
     * Credits {@code amount} to {@code beneficiary}, persists an outbox event,
     * and returns the generated acknowledgment ID.
     */
    @Transactional
    public String applyCredit(String creditRequestId,
                               String transactionId,
                               Account beneficiary,
                               BigDecimal amount,
                               String senderBankCode,
                               String narration) {
        String ackId = UUID.randomUUID().toString();

        BigDecimal balanceBefore = beneficiary.getBalance();
        beneficiary.setBalance(balanceBefore.add(amount));
        accountRepository.save(beneficiary);

        createOutboxEvent(ackId, creditRequestId, transactionId,
                beneficiary, amount, balanceBefore, senderBankCode, narration);

        return ackId;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createOutboxEvent(String ackId,
                                    String creditRequestId,
                                    String transactionId,
                                    Account beneficiary,
                                    BigDecimal amount,
                                    BigDecimal balanceBefore,
                                    String senderBankCode,
                                    String narration) {
        String payload = String.format(
                "{\"ackId\":\"%s\",\"creditRequestId\":\"%s\",\"transactionId\":\"%s\"," +
                "\"beneficiaryAccountId\":%d,\"beneficiaryAccountNumber\":\"%s\"," +
                "\"senderBankCode\":\"%s\",\"amount\":\"%s\"," +
                "\"balanceBefore\":\"%s\",\"balanceAfter\":\"%s\",\"narration\":\"%s\"}",
                ackId, creditRequestId, transactionId,
                beneficiary.getId(), beneficiary.getAccountNumber(),
                senderBankCode, amount.toPlainString(),
                balanceBefore.toPlainString(), beneficiary.getBalance().toPlainString(),
                narration);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(creditRequestId);
        event.setAggregateType("IncomingCredit");
        event.setEventType("CreditApplied");
        event.setTopic(CREDIT_TOPIC);
        event.setPayload(payload);
        outboxEventRepository.save(event);
    }
}
