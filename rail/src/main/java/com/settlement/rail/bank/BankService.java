package com.settlement.rail.bank;

import java.util.List;

public interface BankService {

    Bank create(BankRequest request);

    Bank getById(Long id);

    Bank getByBankCode(String bankCode);

    List<Bank> getAll();

    List<Bank> getAllByStatus(BankStatus status);

    Bank update(Long id, BankRequest request);

    Bank activate(Long id);

    Bank deactivate(Long id);

    void delete(Long id);
}
