package com.settlement.rail.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByReference(String reference);

    boolean existsByReference(String reference);

    List<Transfer> findAllByFromBankId(Long fromBankId);

    List<Transfer> findAllByToBankId(Long toBankId);

    List<Transfer> findAllByStatus(TransferStatus status);
}
