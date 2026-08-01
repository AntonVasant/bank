package com.settlement.rail.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByUtr(String utr);

    boolean existsByUtr(String utr);

    List<Transfer> findAllByFromBankId(Long fromBankId);

    List<Transfer> findAllByToBankId(Long toBankId);

    List<Transfer> findAllByStatus(TransferStatus status);

    List<Transfer> findAllByStatusAndUpdatedAtBefore(TransferStatus status, Instant cutoff);
}
