package com.payment.transaction.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface LedgerEntryLineRepository extends JpaRepository<LedgerEntryLine, Long> {

    /**
     * Sums the amount of every {@code entryType} entry against
     * {@code accountId} (typically this bank's {@code SettlementAccount})
     * for transactions tagged with {@code cycleId}/{@code currency} and
     * already {@code status} {@code POSTED} — used by
     * {@code ReconciliationService} to independently recompute this bank's
     * own net position for a settlement cycle directly from the ledger,
     * rather than from {@code Payment}/{@code IncomingCredit} directly.
     * {@code PENDING} (still in flight) and {@code VOIDED} (never actually
     * moved) transactions are deliberately excluded — they never affected
     * any real balance.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM LedgerEntryLine e
            WHERE e.transaction.cycleId = :cycleId
              AND e.transaction.currency = :currency
              AND e.transaction.status = com.payment.transaction.ledger.LedgerTransactionStatus.POSTED
              AND e.accountId = :accountId
              AND e.entryType = :entryType
            """)
    BigDecimal sumPostedAmount(@Param("cycleId") long cycleId,
                                @Param("currency") String currency,
                                @Param("accountId") Long accountId,
                                @Param("entryType") LedgerEntryType entryType);

    /**
     * Counts the distinct, already-{@code POSTED} ledger transactions
     * against {@code accountId} for {@code cycleId}/{@code currency} —
     * matches the rail's own convention of counting one transfer per side
     * (outgoing hold or incoming credit) it was involved in during the cycle.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.transaction.transactionId)
            FROM LedgerEntryLine e
            WHERE e.transaction.cycleId = :cycleId
              AND e.transaction.currency = :currency
              AND e.transaction.status = com.payment.transaction.ledger.LedgerTransactionStatus.POSTED
              AND e.accountId = :accountId
            """)
    long countPostedTransactions(@Param("cycleId") long cycleId,
                                  @Param("currency") String currency,
                                  @Param("accountId") Long accountId);
}
