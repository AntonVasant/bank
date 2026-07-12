package com.settlement.rail.transfer;

import java.util.List;

public interface TransferService {

    TransferResponse initiate(TransferRequest request);

    TransferResponse getById(Long id);

    TransferResponse getByReference(String reference);

    List<TransferResponse> getAll();

    List<TransferResponse> getAllByStatus(TransferStatus status);

    List<TransferResponse> getAllByFromBank(Long fromBankId);

    List<TransferResponse> getAllByToBank(Long toBankId);
}
