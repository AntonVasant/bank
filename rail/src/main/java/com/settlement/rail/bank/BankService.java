package com.settlement.rail.bank;

import java.util.List;

public interface BankService {

    BankResponse create(BankRequest request);

    BankResponse getById(Long id);

    BankResponse getByBankCode(String bankCode);

    List<BankResponse> getAll();

    List<BankResponse> getAllByStatus(BankStatus status);

    BankResponse update(Long id, BankRequest request);

    BankResponse activate(Long id);

    BankResponse deactivate(Long id);

    void delete(Long id);
}
