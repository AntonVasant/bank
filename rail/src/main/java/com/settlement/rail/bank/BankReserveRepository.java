package com.settlement.rail.bank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankReserveRepository extends JpaRepository<BankReserve, Long> {

    Optional<BankReserve> findByBankIdAndCurrency(Long bankId, String currency);

    List<BankReserve> findAllByBankId(Long bankId);

    boolean existsByBankIdAndCurrency(Long bankId, String currency);
}
