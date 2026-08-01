package com.settlement.rail.ledger;

import com.settlement.rail.bank.Bank;
import com.settlement.rail.bank.BankReserve;
import com.settlement.rail.bank.BankReserveRepository;
import com.settlement.rail.payment.Payment;
import com.settlement.rail.payment.PaymentLedgerStatus;
import com.settlement.rail.payment.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * All business logic for posting settled payments to the Postgres ledger
 * lives here. Like {@code SettlementServiceImpl}, {@link #postCycleToLedger}
 * commits one payment at a time (via a manually-scoped {@code REQUIRES_NEW}
 * transaction) so a failure on a single payment never blocks or rolls back
 * the others already posted in the same run.
 * <p>
 * Purely an audit trail: {@link BankReserve#getBalance()} is mutated
 * separately, from the calculated net settlement amount (see
 * {@code SettlementServiceImpl}), never here.
 */
@Slf4j
@Service
public class SettlementLedgerServiceImpl implements SettlementLedgerService {

    private final PaymentRepository paymentRepository;
    private final BankReserveRepository bankReserveRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionTemplate newTransactionTemplate;

    public SettlementLedgerServiceImpl(PaymentRepository paymentRepository,
                                        BankReserveRepository bankReserveRepository,
                                        LedgerTransactionRepository ledgerTransactionRepository,
                                        LedgerEntryRepository ledgerEntryRepository,
                                        PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.bankReserveRepository = bankReserveRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void postCycleToLedger(Long cycleId) {
        List<Long> pendingIds = paymentRepository
                .findAllByCycleIdAndStatus(cycleId, PaymentLedgerStatus.PENDING)
                .stream().map(Payment::getId).toList();

        int posted = 0;
        for (Long id : pendingIds) {
            try {
                newTransactionTemplate.executeWithoutResult(status -> postOne(id));
                posted++;
            } catch (Exception e) {
                log.error("Failed to post payment id={} (cycleId={}) to the ledger; it remains PENDING " +
                        "and will be retried on the next run", id, cycleId, e);
            }
        }
        log.info("Posted {} of {} pending payment(s) to the ledger for cycleId={}",
                posted, pendingIds.size(), cycleId);
    }

    private void postOne(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found with id: " + paymentId));

        if (payment.getStatus() == PaymentLedgerStatus.POSTED) {
            log.debug("Payment id={} already POSTED; skipping", paymentId);
            return;
        }

        Long debitAccountId = reserveAccountId(payment.getFromBank(), payment.getCurrency());
        Long creditAccountId = reserveAccountId(payment.getToBank(), payment.getCurrency());

        LedgerTransaction transaction = ledgerTransactionRepository.save(LedgerTransaction.builder()
                .paymentId(paymentId)
                .status(LedgerTransactionStatus.POSTED)
                .build());

        saveEntry(transaction, debitAccountId, LedgerEntryType.DEBIT, payment.getAmount());
        saveEntry(transaction, creditAccountId, LedgerEntryType.CREDIT, payment.getAmount());

        payment.setStatus(PaymentLedgerStatus.POSTED);
        payment.setLedgerTransactionId(transaction.getTransactionId());
        payment.setPostedAt(Instant.now());
        paymentRepository.save(payment);

        log.info("Payment id={} posted to ledger as transaction {} (fromBank={} toBank={} amount={} {})",
                paymentId, transaction.getTransactionId(), payment.getFromBank().getBankCode(),
                payment.getToBank().getBankCode(), payment.getAmount(), payment.getCurrency());
    }

    /** Every bank's settlement/reserve account already exists by the time a payment reaches the ledger step (created in {@code TransferServiceImpl.findOrCreateReserve} at transfer time). */
    private Long reserveAccountId(Bank bank, String currency) {
        return bankReserveRepository.findByBankIdAndCurrency(bank.getId(), currency)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bank reserve not found for bankId=%d currency=%s".formatted(bank.getId(), currency)))
                .getId();
    }

    private void saveEntry(LedgerTransaction transaction, Long accountId, LedgerEntryType entryType, BigDecimal amount) {
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction)
                .accountId(accountId)
                .entryType(entryType)
                .amount(amount)
                .build());
    }
}
