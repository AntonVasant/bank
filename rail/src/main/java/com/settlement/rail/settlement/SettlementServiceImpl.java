package com.settlement.rail.settlement;

import com.settlement.rail.bank.Bank;
import com.settlement.rail.bank.BankRepository;
import com.settlement.rail.bank.BankReserve;
import com.settlement.rail.bank.BankReserveRepository;
import com.settlement.rail.bank.CentralReserveAccount;
import com.settlement.rail.bank.CentralReserveAccountRepository;
import com.settlement.rail.cycle.ReconciliationStatus;
import com.settlement.rail.cycle.SettlementCycle;
import com.settlement.rail.cycle.SettlementCycleRepository;
import com.settlement.rail.ledger.LedgerEntry;
import com.settlement.rail.ledger.LedgerEntryRepository;
import com.settlement.rail.ledger.LedgerEntryType;
import com.settlement.rail.ledger.LedgerTransaction;
import com.settlement.rail.ledger.LedgerTransactionRepository;
import com.settlement.rail.ledger.LedgerTransactionStatus;
import com.settlement.rail.outbox.OutboxEvent;
import com.settlement.rail.outbox.OutboxEventRepository;
import com.settlement.rail.payment.PaymentBankAggregate;
import com.settlement.rail.payment.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All business logic for the Settlement batch job lives here \u2014 tasklets are
 * thin wrappers that delegate to these methods. Each public method persists
 * its result directly to application tables so every step is independently
 * restartable, even after a crash mid-way.
 * <p>
 * {@link #applySettlement()} and {@link #createOutbox()} commit one row at a
 * time (via a manually-scoped {@code REQUIRES_NEW} transaction) so a failure
 * on a single bank/currency never blocks or rolls back the others already
 * processed in the same run.
 */
@Slf4j
@Service
public class SettlementServiceImpl implements SettlementService {

    private static final String NET_POSITION_TOPIC = "settlement.net-position.events";

    private final SettlementCycleRepository settlementCycleRepository;
    private final PaymentRepository paymentRepository;
    private final BankRepository bankRepository;
    private final SettlementRepository settlementRepository;
    private final BankReserveRepository bankReserveRepository;
    private final CentralReserveAccountRepository centralReserveAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate newTransactionTemplate;

    public SettlementServiceImpl(SettlementCycleRepository settlementCycleRepository,
                                  PaymentRepository paymentRepository,
                                  BankRepository bankRepository,
                                  SettlementRepository settlementRepository,
                                  BankReserveRepository bankReserveRepository,
                                  CentralReserveAccountRepository centralReserveAccountRepository,
                                  LedgerTransactionRepository ledgerTransactionRepository,
                                  LedgerEntryRepository ledgerEntryRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  PlatformTransactionManager transactionManager) {
        this.settlementCycleRepository = settlementCycleRepository;
        this.paymentRepository = paymentRepository;
        this.bankRepository = bankRepository;
        this.settlementRepository = settlementRepository;
        this.bankReserveRepository = bankReserveRepository;
        this.centralReserveAccountRepository = centralReserveAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    // ── Step 1: Calculate Settlement ────────────────────────────────────────

    @Override
    public void calculateSettlement(Long cycleId) {
        newTransactionTemplate.executeWithoutResult(status -> doCalculateSettlement(cycleId));
    }

    private void doCalculateSettlement(Long cycleId) {
        if (!settlementRepository.findAllByCycleId(cycleId).isEmpty()) {
            log.info("Settlement rows already exist for cycleId={}; skipping recalculation (restart-safe)", cycleId);
            return;
        }

        SettlementCycle cycle = settlementCycleRepository.findById(cycleId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement cycle not found with id: " + cycleId));

        // Net position is derived directly from Payment — the real-time record
        // of every transfer — rather than a separate ledger-entries table:
        // each Payment IS the debit (fromBank) and credit (toBank) side of one
        // transfer, so no separate DEBIT/CREDIT rows are needed.
        List<PaymentBankAggregate> debits = paymentRepository.aggregateDebitsByBankForCycle(cycle.getId());
        List<PaymentBankAggregate> credits = paymentRepository.aggregateCreditsByBankForCycle(cycle.getId());

        Map<String, Settlement> settlementsByBankAndCurrency = new LinkedHashMap<>();

        for (PaymentBankAggregate debit : debits) {
            String key = debit.getBankId() + ":" + debit.getCurrency();
            Settlement settlement = settlementsByBankAndCurrency.computeIfAbsent(key,
                    k -> newSettlement(cycleId, debit));
            settlement.setTransactionCount(settlement.getTransactionCount() + debit.getEntryCount().intValue());
            settlement.setTotalDebit(settlement.getTotalDebit().add(debit.getTotalAmount()));
        }

        for (PaymentBankAggregate credit : credits) {
            String key = credit.getBankId() + ":" + credit.getCurrency();
            Settlement settlement = settlementsByBankAndCurrency.computeIfAbsent(key,
                    k -> newSettlement(cycleId, credit));
            settlement.setTransactionCount(settlement.getTransactionCount() + credit.getEntryCount().intValue());
            settlement.setTotalCredit(settlement.getTotalCredit().add(credit.getTotalAmount()));
        }

        int saved = 0;
        for (Settlement settlement : settlementsByBankAndCurrency.values()) {
            settlement.setNetAmount(settlement.getTotalCredit().subtract(settlement.getTotalDebit()));
            settlement.setChecksum(computeChecksum(settlement));
            settlementRepository.save(settlement);
            saved++;
        }

        log.info("Step 1 (Calculate): persisted {} settlement row(s) for cycleId={}", saved, cycleId);
    }

    private Settlement newSettlement(Long cycleId, PaymentBankAggregate aggregate) {
        Bank bank = bankRepository.findById(aggregate.getBankId())
                .orElseThrow(() -> new EntityNotFoundException("Bank not found with id: " + aggregate.getBankId()));

        return Settlement.builder()
                .cycleId(cycleId)
                .bank(bank)
                .bankCode(aggregate.getBankCode())
                .currency(aggregate.getCurrency())
                .totalCredit(BigDecimal.ZERO)
                .totalDebit(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .transactionCount(0)
                .status(SettlementStatus.PENDING)
                .outboxCreated(false)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .build();
    }

    private String computeChecksum(Settlement settlement) {
        String canonical = "%s|%s|%s|%s|%d".formatted(
                settlement.getCycleId(), settlement.getBankCode(), settlement.getCurrency(),
                settlement.getNetAmount().toPlainString(), settlement.getTransactionCount());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Step 2: Apply Settlement ─────────────────────────────────────────────

    @Override
    public void applySettlement() {
        List<Long> pendingIds = settlementRepository.findAllByStatus(SettlementStatus.PENDING)
                .stream().map(Settlement::getId).toList();

        int applied = 0;
        for (Long id : pendingIds) {
            try {
                newTransactionTemplate.executeWithoutResult(status -> applyOne(id));
                applied++;
            } catch (Exception e) {
                log.error("Step 2 (Apply): failed to apply settlement id={}; it remains PENDING " +
                        "and will be retried on the next run", id, e);
            }
        }
        log.info("Step 2 (Apply): applied {} of {} pending settlement(s)", applied, pendingIds.size());
    }

    private void applyOne(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement not found with id: " + settlementId));

        if (settlement.getStatus() == SettlementStatus.APPLIED) {
            log.debug("Settlement id={} already APPLIED; skipping", settlementId);
            return;
        }

        BankReserve reserve = bankReserveRepository
                .findByBankIdAndCurrency(settlement.getBank().getId(), settlement.getCurrency())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bank reserve not found for bankId=%d currency=%s"
                                .formatted(settlement.getBank().getId(), settlement.getCurrency())));

        reserve.setBalance(reserve.getBalance().add(settlement.getNetAmount()));
        reserve.setReservedBalance(reserve.getReservedBalance().subtract(settlement.getTotalDebit()));
        bankReserveRepository.save(reserve);

        postNetSettlementToLedger(settlement, reserve);

        settlement.setStatus(SettlementStatus.APPLIED);
        settlement.setAppliedAt(Instant.now());
        settlementRepository.save(settlement);

        log.info("Settlement id={} bankCode={} currency={} netAmount={} applied to reserve balance",
                settlementId, settlement.getBankCode(), settlement.getCurrency(), settlement.getNetAmount());
    }

    /**
     * Records this bank's net settlement movement as a real double-entry
     * ledger transaction against the central reserve account — the
     * counterparty for every bank's net position, so the sum of its
     * movements across a fully-applied cycle is always zero (every bank's
     * debit is another's credit). Skipped for a zero net amount — nothing
     * moved, so there's nothing to record.
     * <pre>
     * netAmount &lt; 0 (bank owes):  Debit BankReserve          / Credit CentralReserveAccount
     * netAmount &gt; 0 (bank owed):  Debit CentralReserveAccount / Credit BankReserve
     * </pre>
     */
    private void postNetSettlementToLedger(Settlement settlement, BankReserve reserve) {
        BigDecimal netAmount = settlement.getNetAmount();
        if (netAmount.signum() == 0) {
            return;
        }

        CentralReserveAccount centralReserve = ensureCentralReserveAccount();
        BigDecimal amount = netAmount.abs();

        LedgerTransaction transaction = ledgerTransactionRepository.save(LedgerTransaction.builder()
                .paymentId(settlement.getId())
                .status(LedgerTransactionStatus.POSTED)
                .build());

        if (netAmount.signum() < 0) {
            saveEntry(transaction, reserve.getId(), LedgerEntryType.DEBIT, amount);
            saveEntry(transaction, centralReserve.getId(), LedgerEntryType.CREDIT, amount);
        } else {
            saveEntry(transaction, centralReserve.getId(), LedgerEntryType.DEBIT, amount);
            saveEntry(transaction, reserve.getId(), LedgerEntryType.CREDIT, amount);
        }

        centralReserve.setBalance(centralReserve.getBalance().subtract(netAmount));
        centralReserveAccountRepository.save(centralReserve);
    }

    private void saveEntry(LedgerTransaction transaction, Long accountId, LedgerEntryType entryType, BigDecimal amount) {
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction)
                .accountId(accountId)
                .entryType(entryType)
                .amount(amount)
                .build());
    }

    /** Finds the single central reserve account, creating it (with a zero balance) on first use. */
    private CentralReserveAccount ensureCentralReserveAccount() {
        return centralReserveAccountRepository.findById(CentralReserveAccount.CENTRAL_RESERVE_ACCOUNT_ID)
                .orElseGet(() -> centralReserveAccountRepository.save(CentralReserveAccount.builder()
                        .id(CentralReserveAccount.CENTRAL_RESERVE_ACCOUNT_ID)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }

    // ── Step 3: Create Outbox ────────────────────────────────────────────────

    @Override
    public void createOutbox() {
        List<Long> ids = settlementRepository.findAllByStatusAndOutboxCreatedFalse(SettlementStatus.APPLIED)
                .stream().map(Settlement::getId).toList();

        int created = 0;
        for (Long id : ids) {
            try {
                newTransactionTemplate.executeWithoutResult(status -> createOutboxOne(id));
                created++;
            } catch (Exception e) {
                log.error("Step 3 (Create Outbox): failed to create outbox event for settlement id={}; " +
                        "outboxCreated remains false and it will be retried on the next run", id, e);
            }
        }
        log.info("Step 3 (Create Outbox): created {} of {} outbox event(s)", created, ids.size());
    }

    private void createOutboxOne(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement not found with id: " + settlementId));

        if (settlement.isOutboxCreated()) {
            log.debug("Outbox event already created for settlement id={}; skipping", settlementId);
            return;
        }

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("SETTLEMENT_NET_POSITION")
                .aggregateId(settlement.getId().toString())
                .eventType("SETTLEMENT_NET_POSITION_CALCULATED")
                .topic(NET_POSITION_TOPIC)
                .payload(buildNetPositionPayload(settlement))
                .build());

        settlement.setOutboxCreated(true);
        settlement.setOutboxCreatedAt(Instant.now());
        settlementRepository.save(settlement);
    }

    private String buildNetPositionPayload(Settlement settlement) {
        return """
                {"positionId":%d,"cycleId":"%s","bankCode":"%s","currency":"%s","netAmount":"%s","transactionCount":%d,"generatedAt":"%s"}"""
                .formatted(
                        settlement.getId(),
                        settlement.getCycleId(),
                        settlement.getBankCode(),
                        settlement.getCurrency(),
                        settlement.getNetAmount().toPlainString(),
                        settlement.getTransactionCount(),
                        settlement.getCreatedAt()
                );
    }
}
