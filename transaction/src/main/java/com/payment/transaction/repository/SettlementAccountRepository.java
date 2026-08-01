package com.payment.transaction.repository;

import com.payment.transaction.entity.SettlementAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, Long> {
}
