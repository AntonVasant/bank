package com.payment.transaction.service;

import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.SettlementAccount;
import com.payment.transaction.ledger.LedgerEntryLine;
import com.payment.transaction.ledger.LedgerEntryLineRepository;
import com.payment.transaction.ledger.LedgerEntryType;
import com.payment.transaction.ledger.LedgerTransaction;
import com.payment.transaction.ledger.LedgerTransactionRepository;
import com.payment.transaction.ledger.LedgerTransactionStatus;
import com.payment.transaction.repository.SettlementAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Wires this bank's customer accounts into a Postgres-backed double-entry
 * ledger ({@link LedgerTransaction} / {@link LedgerEntryLine}) so interbank
 * payments use real two-phase (hold / post / void) accounting instead of a
 * direct, unaudited balance mutation - replacing what TigerBeetle used to
 * provide:
 * <pre>
 * Sender (Commercial) bank:   Debit customer account      / Credit settlement account
 * Receiver bank:              Debit settlement account    / Credit customer account
 * </pre>
 * <ul>
 *   <li>{@link #holdFunds} - records a PENDING ledger transaction (customer
 *       to settlement account) the moment a payment is accepted (Moment 1:
 *       lock funds, do not move the settlement account balance yet).</li>
 *   <li>{@link #postHold} - finalizes the hold, crediting this bank's
 *       settlement account, only once the receiver bank has confirmed the
 *       credit (Moment 3) - the real-time ledger posting.</li>
 *   <li>{@link #voidHold} - releases the hold with no funds ever having
 *       moved, if the receiver bank rejects the credit or the round trip
 *       fails.</li>
 *   <li>{@link #creditAccount} - the receiving side (settlement account to
 *       customer): an immediate (already-POSTED) debit of the settlement
 *       account and credit of the beneficiary, once the beneficiary account
 *       has been validated.</li>
 * </ul>
 * Every customer account is referenced directly by its own {@code Account.id}
 * - no separate ledger-side account needs to be created for it. This bank
 * instance additionally has exactly one settlement account (see
 * {@link SettlementAccount}), whose balance this service is solely
 * responsible for mutating.
 */
@Slf4j
@Service
public class AccountLedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryLineRepository ledgerEntryRepository;
    private final SettlementAccountRepository settlementAccountRepository;

    public AccountLedgerService(LedgerTransactionRepository ledgerTransactionRepository,
                                 LedgerEntryLineRepository ledgerEntryRepository,
                                 SettlementAccountRepository settlementAccountRepository) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.settlementAccountRepository = settlementAccountRepository;
    }

    /**
     * Places a PENDING ledger transaction holding {@code amount} of
     * {@code source}'s funds - debits the customer account and credits this
     * bank's settlement account, without moving the settlement account's
     * balance yet. Returns the ledger transaction id, to be posted or voided
     * once the interbank round trip resolves.
     */
    public Long holdFunds(Account source, BigDecimal amount, String reference, long cycleId, Long paymentId) {
        SettlementAccount settlementAccount = ensureSettlementAccount();

        LedgerTransaction transaction = ledgerTransactionRepository.save(LedgerTransaction.builder()
                .paymentId(paymentId)
                .status(LedgerTransactionStatus.PENDING)
                .cycleId(cycleId)
                .currency(source.getCurrency().name())
                .build());

        saveEntry(transaction, source.getId(), LedgerEntryType.DEBIT, amount);
        saveEntry(transaction, settlementAccount.getId(), LedgerEntryType.CREDIT, amount);

        log.info("Placed ledger hold {} for reference={} amount={} cycleId={}",
                transaction.getTransactionId(), reference, amount, cycleId);
        return transaction.getTransactionId();
    }

    /**
     * Finalizes a previously-placed hold (Moment 3) - the real-time ledger
     * posting, crediting this bank's settlement account with the held
     * amount. The customer side of the balance is mutated separately by
     * {@code PaymentService}. Also retags the transaction with {@code cycleId}
     * - the confirmed cycle id from the rail's acknowledgement, which may
     * differ from the provisional one cached at hold time (cycle-mismatch
     * retry) - so reconciliation always aggregates against the cycle this
     * payment actually settled in.
     */
    public void postHold(Long ledgerTransactionId, BigDecimal amount, long cycleId) {
        LedgerTransaction transaction = findTransaction(ledgerTransactionId);
        transaction.setStatus(LedgerTransactionStatus.POSTED);
        transaction.setCycleId(cycleId);
        ledgerTransactionRepository.save(transaction);

        SettlementAccount settlementAccount = ensureSettlementAccount();
        settlementAccount.setBalance(settlementAccount.getBalance().add(amount));
        settlementAccountRepository.save(settlementAccount);

        log.info("Posted ledger hold {} amount={} cycleId={}", ledgerTransactionId, amount, cycleId);
    }

    /** Releases a previously-placed hold - no funds ever move, so the settlement account balance is untouched. */
    public void voidHold(Long ledgerTransactionId) {
        LedgerTransaction transaction = findTransaction(ledgerTransactionId);
        transaction.setStatus(LedgerTransactionStatus.VOIDED);
        ledgerTransactionRepository.save(transaction);
        log.info("Voided ledger hold {}", ledgerTransactionId);
    }

    /**
     * Immediately debits this bank's settlement account and credits
     * {@code beneficiary} - the receiving side of an interbank payment,
     * applied only after the beneficiary account has been validated.
     */
    public void creditAccount(Account beneficiary, BigDecimal amount, String reference, long cycleId, Long paymentId) {
        SettlementAccount settlementAccount = ensureSettlementAccount();

        LedgerTransaction transaction = ledgerTransactionRepository.save(LedgerTransaction.builder()
                .paymentId(paymentId)
                .status(LedgerTransactionStatus.POSTED)
                .cycleId(cycleId)
                .currency(beneficiary.getCurrency().name())
                .build());

        saveEntry(transaction, settlementAccount.getId(), LedgerEntryType.DEBIT, amount);
        saveEntry(transaction, beneficiary.getId(), LedgerEntryType.CREDIT, amount);

        settlementAccount.setBalance(settlementAccount.getBalance().subtract(amount));
        settlementAccountRepository.save(settlementAccount);

        log.info("Posted ledger credit for reference={} amount={} cycleId={}", reference, amount, cycleId);
    }

    private LedgerTransaction findTransaction(Long ledgerTransactionId) {
        return ledgerTransactionRepository.findById(ledgerTransactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ledger transaction not found with id: " + ledgerTransactionId));
    }

    private void saveEntry(LedgerTransaction transaction, Long accountId, LedgerEntryType entryType, BigDecimal amount) {
        ledgerEntryRepository.save(LedgerEntryLine.builder()
                .transaction(transaction)
                .accountId(accountId)
                .entryType(entryType)
                .amount(amount)
                .build());
    }

    /** Finds this bank instance's singleton settlement account, creating it (with a zero balance) on first use. */
    private SettlementAccount ensureSettlementAccount() {
        return settlementAccountRepository.findById(SettlementAccount.SETTLEMENT_ACCOUNT_ID)
                .orElseGet(() -> settlementAccountRepository.save(SettlementAccount.builder()
                        .id(SettlementAccount.SETTLEMENT_ACCOUNT_ID)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }
}
