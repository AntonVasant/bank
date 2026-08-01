package com.settlement.rail.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

    List<ReconciliationResult> findAllByCycleId(String cycleId);

    List<ReconciliationResult> findAllByMatchedFalse();
}
