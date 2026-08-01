package com.settlement.rail.cycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementCycleRepository extends JpaRepository<SettlementCycle, Long> {

    List<SettlementCycle> findAllByStatus(SettlementCycleStatus status);

    Optional<SettlementCycle> findFirstByStatusOrderByStartTimeDesc(SettlementCycleStatus status);
}
