package com.settlement.rail.settlement;

import com.settlement.rail.cycle.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findAllByCycleId(Long cycleId);

    /** Settlements not yet applied to the bank's reserve balance (Step 2 input) — across all cycles, resumable. */
    List<Settlement> findAllByStatus(SettlementStatus status);

    /** Applied settlements still needing an outbox event (Step 3 input) — across all cycles, resumable. */
    List<Settlement> findAllByStatusAndOutboxCreatedFalse(SettlementStatus status);

    /** Settlements not yet published for reconciliation — across all cycles, resumable. */
    List<Settlement> findAllByReconciliationStatus(ReconciliationStatus reconciliationStatus);

    /** Looks up the row a bank's reconciliation result event refers to, to update its {@link ReconciliationStatus}. */
    Optional<Settlement> findByCycleIdAndBankCodeAndCurrency(Long cycleId, String bankCode, String currency);
}
