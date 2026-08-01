package com.settlement.rail.transfer;

import com.settlement.rail.bank.Bank;
import com.settlement.rail.bank.BankRepository;
import com.settlement.rail.bank.BankReserve;
import com.settlement.rail.bank.BankReserveRepository;
import com.settlement.rail.bank.BankStatus;
import com.settlement.rail.cycle.SettlementCycle;
import com.settlement.rail.cycle.SettlementCycleService;
import com.settlement.rail.outbox.OutboxEvent;
import com.settlement.rail.outbox.OutboxEventRepository;
import com.settlement.rail.payment.Payment;
import com.settlement.rail.payment.PaymentLedgerStatus;
import com.settlement.rail.payment.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferServiceImpl implements TransferService {

    /** How long a transfer may sit in PROCESSING before it's considered stuck (e.g. app crashed mid-flight). */
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final TransferRepository transferRepository;
    private final BankRepository bankRepository;
    private final BankReserveRepository bankReserveRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementCycleService settlementCycleService;

    @Override
    @Transactional
    public TransferResponse initiate(TransferRequest request) {
        if (request.fromBankCode().equalsIgnoreCase(request.toBankCode())) {
            throw new IllegalArgumentException("fromBankCode and toBankCode must be different");
        }

        Bank fromBank = findActiveBank(request.fromBankCode());
        Bank toBank = findActiveBank(request.toBankCode());

//        if (transferRepository.existsByUtr(request.utr())) {
//            throw new IllegalArgumentException("Transfer with UTR '%s' already exists".formatted(request.utr()));
//        }

        String currency = request.currency().toUpperCase();

        BankReserve fromReserve = findOrCreateReserve(fromBank, currency);
        // Ensure the receiver's reserve row exists (created on first use);
        // its balance isn't read here since settlement now aggregates net
        // positions directly from Payment rather than a per-transaction
        // ledger entry.
        findOrCreateReserve(toBank, currency);

        // Central Bank validation (Moment 2): reject up front if the sending
        // bank doesn't have enough unreserved settlement balance to cover
        // this transfer — never forward a transfer to the receiver bank that
        // this rail can't actually settle.
        BigDecimal fromAvailable = fromReserve.getBalance().subtract(fromReserve.getReservedBalance());
        if (fromAvailable.compareTo(request.amount()) < 0) {
            throw new IllegalStateException(
                    "Bank '%s' has insufficient settlement balance: available %s, requested %s"
                            .formatted(fromBank.getBankCode(), fromAvailable, request.amount()));
        }

        SettlementCycle currentCycle = settlementCycleService.getCurrentCycle();

        // Cycle validation (rejection path from the "cycle broadcast" design):
        // the sending bank tags its request with the cycle id it cached from
        // our last cycle-open broadcast. If our cycle has since rotated
        // (race between broadcast and this request landing), reject without
        // touching the DB and hand back the real, currently-open cycle id so
        // the caller can void its stale hold and retry with the same utr.
        if (request.cycleId() != null && !request.cycleId().equals(currentCycle.getId())) {
            log.warn("Rejecting transfer utr={} from bank={}: supplied cycleId={} but current open cycle is {}",
                    request.utr(), fromBank.getBankCode(), request.cycleId(), currentCycle.getId());
            return TransferResponse.cycleMismatch(request.utr(), currentCycle.getId());
        }

        // Persist the transfer record
        Transfer transfer = transferRepository.save(Transfer.builder()
                .utr(request.utr())
                .fromBank(fromBank)
                .toBank(toBank)
                .amount(request.amount())
                .currency(currency)
                .status(TransferStatus.ACCEPTED)
                .description(request.description())
                .senderAccountNumber(request.senderAccountNumber())
                .beneficiaryAccountNumber(request.beneficiaryAccountNumber())
                .cycleId(String.valueOf(currentCycle.getId()))
                .build());

        // Real-time: only lock (reserve) the sender's funds for this pending
        // transfer. The actual reserve `balance` for both banks is only
        // updated at the end of the settlement cycle, from the calculated net
        // amount (see com.settlement.rail.settlement.SettlementServiceImpl) —
        // never per-transaction.
        BigDecimal senderReservedAfter = fromReserve.getReservedBalance().add(request.amount());

        fromReserve.setReservedBalance(senderReservedAfter);
        bankReserveRepository.save(fromReserve);

        // Real-time financial record of this payment, tagged with the same
        // open cycle — the input the settlement job later posts to
        // the ledger (the immutable audit trail) once this cycle closes,
        // and the source settlement calculation aggregates net positions from.
        paymentRepository.save(Payment.builder()
                .transfer(transfer)
                .utr(request.utr())
                .cycleId(currentCycle.getId())
                .fromBank(fromBank)
                .toBank(toBank)
                .amount(request.amount())
                .currency(currency)
                .status(PaymentLedgerStatus.PENDING)
                .build());

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
    public TransferResponse markProcessing(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        // Only move ACCEPTED -> PROCESSING; never clobber a status that a
        // concurrent/retried call already drove to a terminal state.
        if (transfer.getStatus() == TransferStatus.ACCEPTED) {
            transfer.setStatus(TransferStatus.PROCESSING);
            transfer = transferRepository.save(transfer);
        }
        return TransferResponse.from(transfer);
    }

    @Override
    @Transactional
    public TransferResponse complete(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        // Idempotent: if it's already terminal, leave it as-is so retries
        // (e.g. after a crash right before this method returns) never
        // overwrite a FAILED transfer back to COMPLETED or reset completedAt.
        if (transfer.getStatus() == TransferStatus.COMPLETED || transfer.getStatus() == TransferStatus.FAILED) {
            return TransferResponse.from(transfer);
        }
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(Instant.now());
        return TransferResponse.from(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public TransferResponse fail(Long transferId, String reason) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        if (transfer.getStatus() == TransferStatus.COMPLETED || transfer.getStatus() == TransferStatus.FAILED) {
            return TransferResponse.from(transfer);
        }
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason(reason);
        return TransferResponse.from(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public TransferResponse markCreditRequested(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        transfer.setCreditStatus(CreditLegStatus.REQUESTED);
        return TransferResponse.from(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public TransferResponse markCreditAccepted(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        transfer.setCreditStatus(CreditLegStatus.ACCEPTED);
        return TransferResponse.from(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public TransferResponse markCreditFailed(Long transferId, String reason) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + transferId));
        transfer.setCreditStatus(CreditLegStatus.FAILED);
        transfer.setFailureReason(reason);
        return TransferResponse.from(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public void resetStuckTransfers() {
        Instant cutoff = Instant.now().minus(STUCK_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<Transfer> stuck = transferRepository.findAllByStatusAndUpdatedAtBefore(TransferStatus.PROCESSING, cutoff);
        for (Transfer transfer : stuck) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason("Timed out waiting for receiver bank acknowledgement " +
                    "(stuck in PROCESSING for over " + STUCK_THRESHOLD_MINUTES + " minutes); needs manual reconciliation");
        }
        if (!stuck.isEmpty()) {
            transferRepository.saveAll(stuck);
        }
    }

    @Override
    public TransferResponse getById(Long id) {
        return TransferResponse.from(
                transferRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Transfer not found with id: " + id)));
    }

    @Override
    public TransferResponse getByUtr(String utr) {
        return transferRepository.findByUtr(utr)
                .map(TransferResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Transfer not found with UTR: " + utr));
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
                {"transferId":%d,"utr":"%s","fromBankCode":"%s","toBankCode":"%s","amount":"%s","currency":"%s","status":"%s"}"""
                .formatted(
                        transfer.getId(),
                        transfer.getUtr(),
                        transfer.getFromBank().getBankCode(),
                        transfer.getToBank().getBankCode(),
                        transfer.getAmount().toPlainString(),
                        transfer.getCurrency(),
                        transfer.getStatus().name()
                );
    }
}
