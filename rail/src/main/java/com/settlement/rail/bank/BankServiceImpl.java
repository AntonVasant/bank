package com.settlement.rail.bank;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankServiceImpl implements BankService {

    private final BankRepository bankRepository;

    @Override
    @Transactional
    public Bank create(BankRequest request) {
        if (bankRepository.existsByBankCode(request.bankCode())) {
            throw new IllegalArgumentException("Bank with code '%s' already exists".formatted(request.bankCode()));
        }
        Bank bank = Bank.builder()
                .bankCode(request.bankCode())
                .country(request.country())
                .name(request.name())
                .description(request.description())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .website(request.website())
                .swiftCode(request.swiftCode())
                .status(BankStatus.ACTIVE)
                .build();
        return bankRepository.save(bank);
    }

    @Override
    public Bank getById(Long id) {
        return findById(id);
    }

    @Override
    public Bank getByBankCode(String bankCode) {
        return bankRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Bank not found with code: " + bankCode));
    }

    @Override
    public List<Bank> getAll() {
        return bankRepository.findAll();
    }

    @Override
    public List<Bank> getAllByStatus(BankStatus status) {
        return bankRepository.findAllByStatus(status);
    }

    @Override
    @Transactional
    public Bank update(Long id, BankRequest request) {
        Bank bank = findById(id);
        if (!bank.getBankCode().equals(request.bankCode()) && bankRepository.existsByBankCode(request.bankCode())) {
            throw new IllegalArgumentException("Bank with code '%s' already exists".formatted(request.bankCode()));
        }
        bank.setBankCode(request.bankCode());
        bank.setCountry(request.country());
        bank.setName(request.name());
        bank.setDescription(request.description());
        bank.setContactEmail(request.contactEmail());
        bank.setContactPhone(request.contactPhone());
        bank.setWebsite(request.website());
        bank.setSwiftCode(request.swiftCode());
        return bankRepository.save(bank);
    }

    @Override
    @Transactional
    public Bank activate(Long id) {
        Bank bank = findById(id);
        bank.setStatus(BankStatus.ACTIVE);
        return bankRepository.save(bank);
    }

    @Override
    @Transactional
    public Bank deactivate(Long id) {
        Bank bank = findById(id);
        bank.setStatus(BankStatus.INACTIVE);
        return bankRepository.save(bank);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        bankRepository.delete(findById(id));
    }

    private Bank findById(Long id) {
        return bankRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Bank not found with id: " + id));
    }
}
