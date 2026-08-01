package com.payment.transaction.repository;

import com.payment.transaction.entity.SettlementCycleRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementCycleRecordRepository extends JpaRepository<SettlementCycleRecord, Long> {

    Optional<SettlementCycleRecord> findByCycleIdAndCurrency(String cycleId, String currency);
}
