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
    public BankResponse create(BankRequest request) {
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
        return BankResponse.from(bankRepository.save(bank));
    }

    @Override
    public BankResponse getById(Long id) {
        return BankResponse.from(findById(id));
    }

    @Override
    public BankResponse getByBankCode(String bankCode) {
        return bankRepository.findByBankCode(bankCode)
                .map(BankResponse::from)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Bank not found with code: " + bankCode));
    }

    @Override
    public List<BankResponse> getAll() {
        return bankRepository.findAll().stream().map(BankResponse::from).toList();
    }

    @Override
    public List<BankResponse> getAllByStatus(BankStatus status) {
        return bankRepository.findAllByStatus(status).stream().map(BankResponse::from).toList();
    }

    @Override
    @Transactional
    public BankResponse update(Long id, BankRequest request) {
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
        return BankResponse.from(bankRepository.save(bank));
    }

    @Override
    @Transactional
    public BankResponse activate(Long id) {
        Bank bank = findById(id);
        bank.setStatus(BankStatus.ACTIVE);
        return BankResponse.from(bankRepository.save(bank));
    }

    @Override
    @Transactional
    public BankResponse deactivate(Long id) {
        Bank bank = findById(id);
        bank.setStatus(BankStatus.INACTIVE);
        return BankResponse.from(bankRepository.save(bank));
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
