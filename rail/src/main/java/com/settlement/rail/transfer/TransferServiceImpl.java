package com.settlement.rail.transfer;

import com.settlement.rail.bank.Bank;
import com.settlement.rail.bank.BankRepository;
import com.settlement.rail.bank.BankReserve;
import com.settlement.rail.bank.BankReserveRepository;
import com.settlement.rail.bank.BankStatus;
import com.settlement.rail.ledger.LedgerEntry;
import com.settlement.rail.ledger.LedgerEntryRepository;
import com.settlement.rail.ledger.LedgerEntryType;
import com.settlement.rail.outbox.OutboxEvent;
import com.settlement.rail.outbox.OutboxEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferServiceImpl implements TransferService {

    private final TransferRepository transferRepository;
    private final BankRepository bankRepository;
    private final BankReserveRepository bankReserveRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public TransferResponse initiate(TransferRequest request) {
        if (request.fromBankCode().equalsIgnoreCase(request.toBankCode())) {
            throw new IllegalArgumentException("fromBankCode and toBankCode must be different");
        }

        Bank fromBank = findActiveBank(request.fromBankCode());
        Bank toBank = findActiveBank(request.toBankCode());

//        if (transferRepository.existsByReference(request.reference())) {
//            throw new IllegalArgumentException("Transfer with reference '%s' already exists".formatted(request.reference()));
//        }

        String currency = request.currency().toUpperCase();

        BankReserve fromReserve = findOrCreateReserve(fromBank, currency);
        BankReserve toReserve   = findOrCreateReserve(toBank, currency);

        // Persist the transfer record
        Transfer transfer = transferRepository.save(Transfer.builder()
                .reference(request.reference())
                .fromBank(fromBank)
                .toBank(toBank)
                .amount(request.amount())
                .currency(currency)
                .status(TransferStatus.ACCEPTED)
                .description(request.description())
                .senderAccountNumber(request.senderAccountNumber())
                .beneficiaryAccountNumber(request.beneficiaryAccountNumber())
                .build());

        BigDecimal senderBalanceAfter   = fromReserve.getBalance().subtract(request.amount());
        BigDecimal receiverBalanceAfter = toReserve.getBalance().add(request.amount());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transfer(transfer)
                .bank(fromBank)
                .bankCode(fromBank.getBankCode())
                .entryType(LedgerEntryType.DEBIT)
                .amount(request.amount())
                .currency(currency)
                .balanceAfter(senderBalanceAfter)
                .reference(request.reference())
                .description(request.description())
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transfer(transfer)
                .bank(toBank)
                .bankCode(toBank.getBankCode())
                .entryType(LedgerEntryType.CREDIT)
                .amount(request.amount())
                .currency(currency)
                .balanceAfter(receiverBalanceAfter)
                .reference(request.reference())
                .description(request.description())
                .build());

        fromReserve.setBalance(senderBalanceAfter);
        toReserve.setBalance(receiverBalanceAfter);
        bankReserveRepository.save(fromReserve);
        bankReserveRepository.save(toReserve);

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("TRANSFER")
                .aggregateId(transfer.getId().toString())
                .eventType("TRANSFER_INITIATED")
                .topic("transfer.events")
                .payload(buildPayload(transfer))
                .build());

        return TransferResponse.from(transfer);
    }

    @Override
    @Transactional
    public TransferResponse complete(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(Instant.now());
        return TransferResponse.from(transferRepository.save(transfer));
    }

    @Override
    public TransferResponse getById(Long id) {
        return TransferResponse.from(
                transferRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + id)));
    }

    @Override
    public TransferResponse getByReference(String reference) {
        return transferRepository.findByReference(reference)
                .map(TransferResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with reference: " + reference));
    }

    @Override
    public List<TransferResponse> getAll() {
        return transferRepository.findAll().stream().map(TransferResponse::from).toList();
    }

    @Override
    public List<TransferResponse> getAllByStatus(TransferStatus status) {
        return transferRepository.findAllByStatus(status).stream().map(TransferResponse::from).toList();
    }

    @Override
    public List<TransferResponse> getAllByFromBank(Long fromBankId) {
        return transferRepository.findAllByFromBankId(fromBankId).stream().map(TransferResponse::from).toList();
    }

    @Override
    public List<TransferResponse> getAllByToBank(Long toBankId) {
        return transferRepository.findAllByToBankId(toBankId).stream().map(TransferResponse::from).toList();
    }

    // --- helpers ---

    private Bank findActiveBank(String bankCode) {
        Bank bank = bankRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new EntityNotFoundException("Bank not found with code: " + bankCode));
        if (bank.getStatus() != BankStatus.ACTIVE) {
            throw new IllegalStateException("Bank '%s' is not active".formatted(bankCode));
        }
        return bank;
    }

    private BankReserve findOrCreateReserve(Bank bank, String currency) {
        return bankReserveRepository.findByBankIdAndCurrency(bank.getId(), currency)
                .orElseGet(() -> bankReserveRepository.save(BankReserve.builder()
                        .bank(bank)
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .reservedBalance(BigDecimal.ZERO)
                        .build()));
    }

    private String buildPayload(Transfer transfer) {
        return """
                {"transferId":%d,"reference":"%s","fromBankCode":"%s","toBankCode":"%s","amount":"%s","currency":"%s","status":"%s"}"""
                .formatted(
                        transfer.getId(),
                        transfer.getReference(),
                        transfer.getFromBank().getBankCode(),
                        transfer.getToBank().getBankCode(),
                        transfer.getAmount().toPlainString(),
                        transfer.getCurrency(),
                        transfer.getStatus().name()
                );
    }
}
