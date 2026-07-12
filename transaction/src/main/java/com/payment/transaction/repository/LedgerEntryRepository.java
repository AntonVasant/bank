package com.payment.transaction.repository;

import com.payment.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByPayment_Id(Long paymentId);

    List<LedgerEntry> findByAccount_Id(Long accountId);
}
