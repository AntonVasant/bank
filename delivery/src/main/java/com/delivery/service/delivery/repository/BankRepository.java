package com.delivery.service.delivery.repository;

import com.delivery.service.delivery.entity.Bank;
import com.delivery.service.delivery.entity.BankStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {

    Optional<Bank> findByBankCode(String bankCode);

    List<Bank> findAllByStatus(BankStatus status);

    boolean existsByBankCode(String bankCode);
}
