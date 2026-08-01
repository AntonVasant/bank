package com.payment.transaction.repository;

import com.payment.transaction.entity.IncomingCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncomingCreditRepository extends JpaRepository<IncomingCredit, Long> {

    Optional<IncomingCredit> findByCreditRequestId(String creditRequestId);

    List<IncomingCredit> findAllByCycleId(String cycleId);
}
