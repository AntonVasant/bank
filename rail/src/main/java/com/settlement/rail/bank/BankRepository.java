package com.settlement.rail.bank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {

    Optional<Bank> findByBankCode(String bankCode);

    List<Bank> findAllByStatus(BankStatus status);

    boolean existsByBankCode(String bankCode);
}
