package com.payment.transaction.service;

import com.payment.transaction.dto.CreatePaymentRequest;
import com.payment.transaction.dto.PaymentResponse;
import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.LedgerEntry;
import com.payment.transaction.entity.OutboxEvent;
import com.payment.transaction.entity.Payment;
import com.payment.transaction.repository.AccountRepository;
import com.payment.transaction.repository.LedgerEntryRepository;
import com.payment.transaction.repository.OutboxEventRepository;
import com.payment.transaction.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private static final String PAYMENT_TOPIC = "payments.created";

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;

    public PaymentService(PaymentRepository paymentRepository,
                          AccountRepository accountRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          OutboxEventRepository outboxEventRepository,
                          IdempotencyService idempotencyService) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyService = idempotencyService;
    }

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> checkIdempotency(String idempotencyKey) {
        return idempotencyService.checkExisting(idempotencyKey, PaymentResponse.class);
    }

    @Transactional
    public void registerIdempotencyKey(String idempotencyKey) {
        idempotencyService.register(idempotencyKey, null);
    }

    // ── Source account ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Account loadAndValidateSource(CreatePaymentRequest request) {
        Account source = accountRepository.findByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Source account not found: "));
        if (source.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Source account is not active: " + source.getAccountNumber());
        }
        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Insufficient balance: available " + source.getBalance()
                    + ", requested " + request.amount());
        }
        return source;
    }

    // ── Intra-bank ───────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse executeIntraBank(String idempotencyKey,
                                             CreatePaymentRequest request,
                                             Account source) {
        Account destination = accountRepository.findByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Destination account not found: "));

        if (source.getId().equals(destination.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source and destination accounts must be different");
        }
        if (destination.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Destination account is not active: " + destination.getAccountNumber());
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Currency mismatch: source=" + source.getCurrency()
                    + " destination=" + destination.getCurrency());
        }

        BigDecimal sourceBalanceBefore      = source.getBalance();
        BigDecimal destinationBalanceBefore = destination.getBalance();

        source.setBalance(sourceBalanceBefore.subtract(request.amount()));
        destination.setBalance(destinationBalanceBefore.add(request.amount()));
        accountRepository.save(source);
        accountRepository.save(destination);

        Payment payment = buildPayment(request, source);
        payment.setDestinationAccount(destination);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        Payment saved = paymentRepository.save(payment);

        createIntraBankLedgerEntries(saved, source, destination, sourceBalanceBefore, destinationBalanceBefore);
        createOutboxEvent(saved);

        PaymentResponse response = toResponse(saved);
        idempotencyService.markComplete(idempotencyKey, saved.getId(), response);
        return response;
    }

    // ── Inter-bank ───────────────────────────────────────────────────────────

    /** TX-1b (CB acknowledged): move payment from PROCESSING → CB_ACKNOWLEDGED. */
    @Transactional
    public void markCbAcknowledged(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment not found during CB acknowledgement: " + paymentId));
        payment.setStatus(Payment.PaymentStatus.CB_ACKNOWLEDGED);
        paymentRepository.save(payment);
    }

    /** TX-1: debit source, persist PROCESSING payment and ledger entries. Commits before network call. */
    @Transactional
    public Payment prepareInterBankPayment(String idempotencyKey,
                                            CreatePaymentRequest request,
                                            Account source) {
        BigDecimal sourceBalanceBefore = source.getBalance();
        source.setBalance(sourceBalanceBefore.subtract(request.amount()));
        accountRepository.save(source);

        Payment payment = buildPayment(request, source);
        payment.setDestinationBankCode(request.destinationBankCode());
        payment.setDestinationExternalAccount(request.destinationAccountNumber());
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setDestinationExternalAccount(request.destinationAccountNumber());
        Payment saved = paymentRepository.save(payment);

        createInterBankLedgerEntries(saved, source, sourceBalanceBefore);
        return saved;
    }

    /** TX-2 (success): mark COMPLETED, post credit ledger entry, outbox event, idempotency record. */
    @Transactional
    public PaymentResponse completeInterBankPayment(String idempotencyKey,
                                                     Long paymentId,
                                                     String settlementRef) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment lost after settlement: " + paymentId));
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        ledgerEntryRepository.findByPayment_Id(paymentId).stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .findFirst()
                .ifPresent(e -> {
                    e.setStatus(LedgerEntry.EntryStatus.POSTED);
                    e.setPostedAt(LocalDateTime.now());
                    e.setDescription(e.getDescription() + " | settlement: " + settlementRef);
                    ledgerEntryRepository.save(e);
                });

        PaymentResponse response = toResponse(payment);
        idempotencyService.markComplete(idempotencyKey, payment.getId(), response);
        return response;
    }

    /** TX-3 (failure): refund the debit and mark the payment as FAILED. */
    @Transactional
    public void refundInterBankPayment(Long paymentId, Long sourceAccountId, BigDecimal amount) {
        Account source = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Source account not found during refund"));
        source.setBalance(source.getBalance().add(amount));
        accountRepository.save(source);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment not found during refund: " + paymentId));
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Payment buildPayment(CreatePaymentRequest request, Account source) {
        Payment payment = new Payment();
        payment.setPaymentReference(request.paymentReference());
        payment.setSourceAccount(source);
        payment.setAmount(request.amount());
        payment.setProcessedAt(LocalDateTime.now());
        return payment;
    }

    private void createIntraBankLedgerEntries(Payment payment, Account source, Account destination,
                                               BigDecimal sourceBalanceBefore,
                                               BigDecimal destinationBalanceBefore) {
        LocalDateTime now = LocalDateTime.now();

        LedgerEntry debit = new LedgerEntry();
        debit.setEntryReference(payment.getPaymentReference() + "-DEBIT");
        debit.setPayment(payment);
        debit.setAccount(source);
        debit.setSourceAccountNumber(source.getAccountNumber());
        debit.setDestinationAccountNumber(destination.getAccountNumber());
        debit.setEntryType(LedgerEntry.EntryType.DEBIT);
        debit.setAmount(payment.getAmount());
        debit.setCurrency(source.getCurrency());
        debit.setBalanceBefore(sourceBalanceBefore);
        debit.setBalanceAfter(source.getBalance());
        debit.setStatus(LedgerEntry.EntryStatus.POSTED);
        debit.setPostedAt(now);
        debit.setDescription("Debit for payment " + payment.getPaymentReference());

        LedgerEntry credit = new LedgerEntry();
        credit.setEntryReference(payment.getPaymentReference() + "-CREDIT");
        credit.setPayment(payment);
        credit.setAccount(destination);
        credit.setSourceAccountNumber(source.getAccountNumber());
        credit.setDestinationAccountNumber(destination.getAccountNumber());
        credit.setEntryType(LedgerEntry.EntryType.CREDIT);
        credit.setAmount(payment.getAmount());
        credit.setCurrency(destination.getCurrency());
        credit.setBalanceBefore(destinationBalanceBefore);
        credit.setBalanceAfter(destination.getBalance());
        credit.setStatus(LedgerEntry.EntryStatus.POSTED);
        credit.setPostedAt(now);
        credit.setDescription("Credit for payment " + payment.getPaymentReference());

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
    }

    private void createInterBankLedgerEntries(Payment payment, Account source,
                                               BigDecimal sourceBalanceBefore) {
        LocalDateTime now = LocalDateTime.now();
        String externalAccount = payment.getDestinationExternalAccount();

        LedgerEntry debit = new LedgerEntry();
        debit.setEntryReference(payment.getPaymentReference() + "-DEBIT");
        debit.setPayment(payment);
        debit.setAccount(source);
        debit.setSourceAccountNumber(source.getAccountNumber());
        debit.setDestinationAccountNumber(externalAccount);
        debit.setEntryType(LedgerEntry.EntryType.DEBIT);
        debit.setAmount(payment.getAmount());
        debit.setCurrency(source.getCurrency());
        debit.setBalanceBefore(sourceBalanceBefore);
        debit.setBalanceAfter(source.getBalance());
        debit.setStatus(LedgerEntry.EntryStatus.POSTED);
        debit.setPostedAt(now);
        debit.setDescription("Debit for inter-bank payment " + payment.getPaymentReference());

        // account is null — external destination, balance managed by receiving bank
        LedgerEntry credit = new LedgerEntry();
        credit.setEntryReference(payment.getPaymentReference() + "-CREDIT");
        credit.setPayment(payment);
        credit.setAccount(null);
        credit.setSourceAccountNumber(source.getAccountNumber());
        credit.setDestinationAccountNumber(externalAccount);
        credit.setEntryType(LedgerEntry.EntryType.CREDIT);
        credit.setAmount(payment.getAmount());
        credit.setCurrency(source.getCurrency());
        credit.setBalanceBefore(BigDecimal.ZERO);
        credit.setBalanceAfter(BigDecimal.ZERO);
        credit.setStatus(LedgerEntry.EntryStatus.PENDING);
        credit.setDescription("Credit for inter-bank payment " + payment.getPaymentReference()
                + " to " + payment.getDestinationBankCode()
                + " / " + externalAccount);

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
    }

    /**
     * Writes a PENDING outbox event so a relay can publish it to Kafka
     * without needing a distributed transaction.
     */
    private void createOutboxEvent(Payment payment) {
        String destinationAccountNumber = payment.getDestinationAccount() != null
                ? payment.getDestinationAccount().getAccountNumber()
                : payment.getDestinationExternalAccount();
        String payload = String.format(
                "{\"paymentId\":%d,\"paymentReference\":\"%s\","
                + "\"sourceAccountNumber\":\"%s\",\"destinationAccountNumber\":\"%s\","
                + "\"amount\":\"%s\",\"status\":\"%s\"}",
                payment.getId(),
                payment.getPaymentReference(),
                payment.getSourceAccount().getAccountNumber(),
                destinationAccountNumber != null ? destinationAccountNumber : "",
                payment.getAmount().toPlainString(),
                payment.getStatus().name()
        );

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(String.valueOf(payment.getId()));
        event.setAggregateType("Payment");
        event.setEventType("PaymentCreated");
        event.setTopic(PAYMENT_TOPIC);
        event.setPayload(payload);

        outboxEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        String destinationAccountNumber = payment.getDestinationAccount() != null
                ? payment.getDestinationAccount().getAccountNumber()
                : payment.getDestinationExternalAccount();
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getSourceAccount().getAccountNumber(),
                destinationAccountNumber,
                payment.getAmount(),
                payment.getStatus().name());
    }
}
