package com.payment.transaction.service;

import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.CreditStatus;
import com.payment.transaction.entity.IncomingCredit;
import com.payment.transaction.entity.OutboxEvent;
import com.payment.transaction.repository.AccountRepository;
import com.payment.transaction.repository.IncomingCreditRepository;
import com.payment.transaction.repository.OutboxEventRepository;
import jakarta.persistence.EntityNotFoundException;
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
 * <p>
 * Each incoming credit request moves through {@link CreditStatus} one
 * durable step at a time ({@link #recordReceived} → {@link #markAccepted} →
 * {@link #applyCredit}, or {@link #markFailed}) so {@link CreditOrchestrator}
 * can recover from a crash at any point without re-crediting the beneficiary
 * or losing track of a request the rail may retry.
 */
@Service
public class CreditService {

    private static final String CREDIT_TOPIC = "credits.applied";

    private final AccountRepository accountRepository;
    private final IncomingCreditRepository incomingCreditRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AccountLedgerService accountLedgerService;

    public CreditService(AccountRepository accountRepository,
                         IncomingCreditRepository incomingCreditRepository,
                         OutboxEventRepository outboxEventRepository,
                         AccountLedgerService accountLedgerService) {
        this.accountRepository = accountRepository;
        this.incomingCreditRepository = incomingCreditRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.accountLedgerService = accountLedgerService;
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

    // ── Credit state machine ─────────────────────────────────────────────────

    /**
     * Moment 1: durably records that a credit request arrived, before any
     * validation or crediting happens — {@code RECEIVED} is the checkpoint
     * that lets a crash between here and {@link #applyCredit} be told apart
     * from a request that was never picked up at all.
     * <p>
     * Idempotent: if a row already exists for this {@code creditRequestId}
     * (the rail retried after not getting our previous acknowledgment back),
     * that existing row — at whatever status it reached — is returned
     * instead of creating a duplicate, so {@link CreditOrchestrator} can
     * decide whether to short-circuit (already {@code COMPLETED}/{@code FAILED})
     * or resume (still {@code RECEIVED}/{@code ACCEPTED}).
     */
    @Transactional
    public IncomingCredit recordReceived(String creditRequestId,
                                          String transactionId,
                                          String cycleId,
                                          String beneficiaryAccountNumber,
                                          BigDecimal amount,
                                          String currency,
                                          String senderBankCode) {
        return incomingCreditRepository.findByCreditRequestId(creditRequestId)
                .orElseGet(() -> {
                    IncomingCredit incomingCredit = new IncomingCredit();
                    incomingCredit.setCreditRequestId(creditRequestId);
                    incomingCredit.setTransactionId(transactionId);
                    incomingCredit.setCycleId(cycleId);
                    incomingCredit.setSenderBankCode(senderBankCode);
                    incomingCredit.setBeneficiaryAccountNumber(beneficiaryAccountNumber);
                    incomingCredit.setAmount(amount);
                    incomingCredit.setCurrency(currency);
                    incomingCredit.setStatus(CreditStatus.RECEIVED);
                    return incomingCreditRepository.save(incomingCredit);
                });
    }

    /** Moment 2: the beneficiary account has been validated — about to apply the credit. */
    @Transactional
    public void markAccepted(Long incomingCreditId) {
        IncomingCredit incomingCredit = findIncomingCredit(incomingCreditId);
        if (incomingCredit.getStatus() == CreditStatus.RECEIVED) {
            incomingCredit.setStatus(CreditStatus.ACCEPTED);
            incomingCreditRepository.save(incomingCredit);
        }
    }

    /**
     * Moment 3: applies {@code amount} to {@code beneficiary} (the
     * beneficiary account has already been validated by
     * {@link #findAndValidateBeneficiary}, so this is applied immediately
     * and permanently via the ledger, no hold needed here since Bank B
     * never has to "undo" a credit the way Bank A undoes a debit), persists
     * an outbox event, marks the {@link IncomingCredit} {@code COMPLETED},
     * and returns the generated acknowledgment ID.
     */
    @Transactional
    public String applyCredit(Long incomingCreditId, Account beneficiary, String narration) {
        IncomingCredit incomingCredit = findIncomingCredit(incomingCreditId);
        String ackId = UUID.randomUUID().toString();

        accountLedgerService.creditAccount(beneficiary, incomingCredit.getAmount(),
                incomingCredit.getCreditRequestId(), parseCycleId(incomingCredit.getCycleId()),
                incomingCredit.getId());

        BigDecimal balanceBefore = beneficiary.getBalance();
        beneficiary.setBalance(balanceBefore.add(incomingCredit.getAmount()));
        accountRepository.save(beneficiary);

        incomingCredit.setStatus(CreditStatus.COMPLETED);
        incomingCreditRepository.save(incomingCredit);

        createOutboxEvent(ackId, incomingCredit, beneficiary, balanceBefore, narration);

        return ackId;
    }

    /** Terminal failure — validation failed or the credit couldn't be applied. Idempotent: a no-op if already COMPLETED. */
    @Transactional
    public void markFailed(Long incomingCreditId, String reason) {
        IncomingCredit incomingCredit = findIncomingCredit(incomingCreditId);
        if (incomingCredit.getStatus() != CreditStatus.COMPLETED) {
            incomingCredit.setStatus(CreditStatus.FAILED);
            incomingCredit.setFailureReason(reason);
            incomingCreditRepository.save(incomingCredit);
        }
    }

    /** Parses the cycle id the central bank supplied on the credit instruction itself; 0 (untagged) if missing/unparseable rather than guessing a value. */
    private static long parseCycleId(String cycleId) {
        if (cycleId == null || cycleId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(cycleId);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IncomingCredit findIncomingCredit(Long incomingCreditId) {
        return incomingCreditRepository.findById(incomingCreditId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incoming credit not found with id: " + incomingCreditId));
    }

    private void createOutboxEvent(String ackId,
                                    IncomingCredit incomingCredit,
                                    Account beneficiary,
                                    BigDecimal balanceBefore,
                                    String narration) {
        String payload = String.format(
                "{\"ackId\":\"%s\",\"creditRequestId\":\"%s\",\"transactionId\":\"%s\"," +
                "\"beneficiaryAccountId\":%d,\"beneficiaryAccountNumber\":\"%s\"," +
                "\"senderBankCode\":\"%s\",\"amount\":\"%s\"," +
                "\"balanceBefore\":\"%s\",\"balanceAfter\":\"%s\",\"narration\":\"%s\"}",
                ackId, incomingCredit.getCreditRequestId(), incomingCredit.getTransactionId(),
                beneficiary.getId(), beneficiary.getAccountNumber(),
                incomingCredit.getSenderBankCode(), incomingCredit.getAmount().toPlainString(),
                balanceBefore.toPlainString(), beneficiary.getBalance().toPlainString(),
                narration);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(incomingCredit.getCreditRequestId());
        event.setAggregateType("IncomingCredit");
        event.setEventType("CreditApplied");
        event.setTopic(CREDIT_TOPIC);
        event.setPayload(payload);
        outboxEventRepository.save(event);
    }
}
