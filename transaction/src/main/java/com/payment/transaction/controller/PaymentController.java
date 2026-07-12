package com.payment.transaction.controller;

import com.payment.transaction.dto.BatchResponse;
import com.payment.transaction.dto.CreateBulkPaymentRequest;
import com.payment.transaction.dto.CreatePaymentRequest;
import com.payment.transaction.dto.PaymentResponse;
import com.payment.transaction.service.PaymentBatchService;
import com.payment.transaction.service.PaymentOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentOrchestrator paymentOrchestrator;
    private final PaymentBatchService paymentBatchService;

    public PaymentController(PaymentOrchestrator paymentOrchestrator,
                              PaymentBatchService paymentBatchService) {
        this.paymentOrchestrator = paymentOrchestrator;
        this.paymentBatchService = paymentBatchService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<PaymentResponse>> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePaymentRequest request) {
        return paymentOrchestrator.createPayment(idempotencyKey, request)
                .thenApply(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentOrchestrator.getPayment(paymentId));
    }

    @PostMapping("/bulk")
    public ResponseEntity<BatchResponse> createBulkPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateBulkPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentBatchService.createBatch(idempotencyKey, request));
    }
}
