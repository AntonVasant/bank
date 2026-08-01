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
    private final AccountLedgerService accountLedgerService;
    private final CycleCacheService cycleCacheService;

    public PaymentService(PaymentRepository paymentRepository,
                          AccountRepository accountRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          OutboxEventRepository outboxEventRepository,
                          IdempotencyService idempotencyService,
                          AccountLedgerService accountLedgerService,
                          CycleCacheService cycleCacheService) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyService = idempotencyService;
        this.accountLedgerService = accountLedgerService;
        this.cycleCacheService = cycleCacheService;
    }


    public Optional<PaymentResponse> checkIdempotency(String idempotencyKey) {
        return idempotencyService.checkExisting(idempotencyKey, PaymentResponse.class);
    }

    public void registerIdempotencyKey(String idempotencyKey) {
        idempotencyService.register(idempotencyKey, null);
    }


    @Transactional(readOnly = true)
    public Account loadAndValidateSource(CreatePaymentRequest request) {
        Account source = accountRepository.findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Source account not found: " + request.sourceAccountNumber()));
        if (source.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Source account is not active: " + source.getAccountNumber());
        }
        if (source.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Insufficient balance: available " + source.getAvailableBalance()
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
      //  idempotencyService.markComplete(idempotencyKey, saved.getId(), response);
        return response;
    }

    // ── Inter-bank ───────────────────────────────────────────────────────────

    /** TX-1b (CB acknowledged): move payment from PROCESSING → CB_ACKNOWLEDGED. */
    @Transactional
    public void markCbAcknowledged(Long paymentId, String cycleId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment not found during CB acknowledgement: " + paymentId));
        payment.setStatus(Payment.PaymentStatus.CB_ACKNOWLEDGED);
        payment.setCycleId(cycleId);
        paymentRepository.save(payment);
    }

    /**
     * Reads the settlement rail's last-broadcast cycle id from Redis (see
     * {@link CycleCacheService}). Deliberately {@code @Transactional}-free —
     * called before {@link #prepareInterBankPayment} so the Redis round
     * trip never happens while a DB transaction (and its connection) is
     * held open.
     */
    public long currentCycleId() {
        return cycleCacheService.getCurrentCycleId();
    }

    /**
     * TX-1 (Moment 1): place a hold — lock {@code request.amount()} of the
     * source account's funds via a PENDING ledger transaction (not yet a
     * permanent debit), tagged with {@code cycleId} — the settlement cycle
     * id cached from the rail's last broadcast (never guessed — see
     * {@link #currentCycleId()}, which the caller must fetch <em>before</em>
     * calling this method, since it's a Redis round trip that shouldn't
     * happen inside this DB transaction), persist a PROCESSING payment and
     * PENDING ledger entries, and commit — all before the network call to
     * the rail.
     */
    @Transactional
    public Payment prepareInterBankPayment(String idempotencyKey,
                                            CreatePaymentRequest request,
                                            Account source,
                                            long cycleId) {
        BigDecimal heldBefore = source.getHeldBalance();
        source.setHeldBalance(heldBefore.add(request.amount()));
        accountRepository.save(source);

        Payment payment = buildPayment(request, source);
        payment.setDestinationBankCode(request.destinationBankCode());
        payment.setDestinationExternalAccount(request.destinationAccountNumber());
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setCycleId(String.valueOf(cycleId));
        Payment saved = paymentRepository.save(payment);

        Long ledgerTransactionId = accountLedgerService.holdFunds(
                source, request.amount(), request.paymentReference(), cycleId, saved.getId());
        saved.setLedgerTransactionId(ledgerTransactionId);
        saved = paymentRepository.save(saved);

        createInterBankLedgerEntries(saved, source);
        createOutboxEvent(saved);
        return saved;
    }

    /**
     * Cycle-mismatch retry (the rail rejected because our cached cycle id
     * had gone stale): re-cache the corrected cycle id and retag the
     * payment with it. The ledger hold itself is untouched — it only
     * records that funds are locked, never which settlement cycle they'll
     * eventually settle in, so there's nothing to void/re-place here.
     */
    @Transactional
    public Payment reholdForNewCycle(Long paymentId, long newCycleId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment not found during cycle retry: " + paymentId));

        cycleCacheService.setCurrentCycleId(newCycleId);
        payment.setCycleId(String.valueOf(newCycleId));
        paymentRepository.save(payment);

        createCycleRetryOutboxEvent(payment, newCycleId);
        return payment;
    }

    /** Builds the response returned to the caller immediately after the hold is placed, and marks the idempotency record complete (Moment 1: the caller never waits for the interbank round trip). */
    @Transactional(readOnly = true)
    public PaymentResponse toProcessingResponseAndMarkIdempotent(String idempotencyKey, Payment payment) {
        PaymentResponse response = toResponse(payment);
        //idempotencyService.markComplete(idempotencyKey, payment.getId(), response);
        return response;
    }

    /**
     * TX-2 (success, Moment 3): the receiver bank confirmed the credit —
     * finalize the hold as a permanent debit, mark COMPLETED, post both
     * ledger entries.
     */
    @Transactional
    public PaymentResponse completeInterBankPayment(String idempotencyKey,
                                                     Long paymentId,
                                                     String settlementRef,
                                                     long cycleId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment lost after settlement: " + paymentId));

        accountLedgerService.postHold(payment.getLedgerTransactionId(), payment.getAmount(), cycleId);

        Account source = payment.getSourceAccount();
        source.setHeldBalance(source.getHeldBalance().subtract(payment.getAmount()));
        source.setBalance(source.getBalance().subtract(payment.getAmount()));
        accountRepository.save(source);

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        LocalDateTime now = LocalDateTime.now();
        ledgerEntryRepository.findByPayment_Id(paymentId).forEach(e -> {
            if (e.getEntryType() == LedgerEntry.EntryType.DEBIT) {
                e.setStatus(LedgerEntry.EntryStatus.POSTED);
                e.setBalanceAfter(source.getBalance());
                e.setPostedAt(now);
                ledgerEntryRepository.save(e);
            } else if (e.getEntryType() == LedgerEntry.EntryType.CREDIT) {
                e.setStatus(LedgerEntry.EntryStatus.POSTED);
                e.setPostedAt(now);
                e.setDescription(e.getDescription() + " | settlement: " + settlementRef);
                ledgerEntryRepository.save(e);
            }
        });

        return toResponse(payment);
    }

    /**
     * TX-3 (failure, Moment 2 — invalid account or round-trip failure): void
     * the hold — no funds ever moved, so nothing needs to be refunded, only
     * the hold released — and mark the payment FAILED.
     */
    @Transactional
    public void refundInterBankPayment(Long paymentId, Long sourceAccountId, BigDecimal amount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Payment not found during void: " + paymentId));

        accountLedgerService.voidHold(payment.getLedgerTransactionId());

        Account source = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Source account not found during void"));
        source.setHeldBalance(source.getHeldBalance().subtract(amount));
        accountRepository.save(source);

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        LocalDateTime now = LocalDateTime.now();
        ledgerEntryRepository.findByPayment_Id(paymentId).forEach(e -> {
            e.setStatus(LedgerEntry.EntryStatus.VOIDED);
            e.setVoidedAt(now);
            ledgerEntryRepository.save(e);
        });
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

    private void createInterBankLedgerEntries(Payment payment, Account source) {
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
        debit.setBalanceBefore(source.getBalance());
        debit.setBalanceAfter(source.getBalance());
        debit.setStatus(LedgerEntry.EntryStatus.PENDING);
        debit.setDescription("Hold for inter-bank payment " + payment.getPaymentReference());

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
                + "\"amount\":\"%s\",\"cycleId\":\"%s\",\"status\":\"%s\"}",
                payment.getId(),
                payment.getPaymentReference(),
                payment.getSourceAccount().getAccountNumber(),
                destinationAccountNumber != null ? destinationAccountNumber : "",
                payment.getAmount().toPlainString(),
                payment.getCycleId() != null ? payment.getCycleId() : "",
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

    /** Records the cycle-mismatch retry (step 19: "updates outbox with new cycle_id") so downstream consumers see the corrected cycle id too. */
    private void createCycleRetryOutboxEvent(Payment payment, long newCycleId) {
        String payload = String.format(
                "{\"paymentId\":%d,\"paymentReference\":\"%s\",\"newCycleId\":%d}",
                payment.getId(), payment.getPaymentReference(), newCycleId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(String.valueOf(payment.getId()));
        event.setAggregateType("Payment");
        event.setEventType("PaymentCycleRetried");
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
