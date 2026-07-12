package com.settlement.rail.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findAllByTransferId(Long transferId);

    List<LedgerEntry> findAllByBankId(Long bankId);

    List<LedgerEntry> findAllByBankIdAndCurrency(Long bankId, String currency);

    List<LedgerEntry> findAllByTransferIdOrderByCreatedAtAsc(Long transferId);

    List<LedgerEntry> findAllByBankIdOrderByCreatedAtDesc(Long bankId);
}
