package com.payment.transaction.service;

import com.payment.transaction.dto.BatchResponse;
import com.payment.transaction.dto.BulkPaymentItem;
import com.payment.transaction.dto.CreateBulkPaymentRequest;
import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.Payment;
import com.payment.transaction.entity.PaymentBatch;
import com.payment.transaction.repository.AccountRepository;
import com.payment.transaction.repository.PaymentBatchRepository;
import com.payment.transaction.repository.PaymentRepository;
import io.micrometer.core.annotation.Timed;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class PaymentBatchService {

    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyService idempotencyService;

    public PaymentBatchService(PaymentBatchRepository paymentBatchRepository,
                               PaymentRepository paymentRepository,
                               AccountRepository accountRepository,
                               IdempotencyService idempotencyService) {
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.idempotencyService = idempotencyService;
    }

    @Timed("batch.create_batch")
    public BatchResponse createBatch(String idempotencyKey, CreateBulkPaymentRequest request) {
        // Return cached response if this batch was already completed
        var cached = idempotencyService.checkExisting(idempotencyKey, BatchResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        PaymentBatch batch = new PaymentBatch();
        batch.setBatchReference(request.batchReference());
        batch.setStatus(PaymentBatch.BatchStatus.PENDING);
        batch.setTotalPayments(request.payments().size());
        batch.setSuccessfulPayments(0);
        batch.setFailedPayments(0);

        // Register key as IN_PROGRESS before saving so concurrent duplicates are rejected
        idempotencyService.register(idempotencyKey, null);

        PaymentBatch savedBatch = paymentBatchRepository.save(batch);

        for (BulkPaymentItem item : request.payments()) {
            Account source = accountRepository.findById(item.sourceAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found: " + item.sourceAccountId()));

            Account destination = accountRepository.findById(item.destinationAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination account not found: " + item.destinationAccountId()));

            Payment payment = new Payment();
            payment.setPaymentReference(item.paymentReference());
            payment.setSourceAccount(source);
            payment.setDestinationAccount(destination);
            payment.setAmount(item.amount());
            payment.setStatus(Payment.PaymentStatus.PENDING);
            payment.setBatch(savedBatch);

            paymentRepository.save(payment);
        }

        // The idempotency cache stores this response (in Redis) for replay on
        // retries, so it has to be built here (unlike the simpler read
        // methods below) — PaymentBatch itself can't be cached directly since
        // its lazy `payments` collection would throw LazyInitializationException
        // if Jackson tried to serialize it outside this transaction.
        BatchResponse response = BatchResponse.from(savedBatch);
        idempotencyService.markComplete(idempotencyKey, savedBatch.getId(), response);

        // TODO: publish OutboxEvent to Kafka

        return response;
    }

    @Timed("batch.get_batch_status")
    @Transactional(readOnly = true)
    public PaymentBatch getBatchStatus(Long batchId) {
        return paymentBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + batchId));
    }

    @Timed("batch.get_batch_payments")
    @Transactional(readOnly = true)
    public List<Payment> getBatchPayments(Long batchId) {
        paymentBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + batchId));

        return paymentRepository.findByBatch_Id(batchId);
    }
}
