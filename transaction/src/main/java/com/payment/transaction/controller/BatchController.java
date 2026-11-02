package com.payment.transaction.controller;

import com.payment.transaction.dto.BatchPaymentsResponse;
import com.payment.transaction.dto.BatchResponse;
import com.payment.transaction.service.PaymentBatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final PaymentBatchService paymentBatchService;

    public BatchController(PaymentBatchService paymentBatchService) {
        this.paymentBatchService = paymentBatchService;
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<BatchResponse> getBatchStatus(@PathVariable Long batchId) {
        return ResponseEntity.ok(BatchResponse.from(paymentBatchService.getBatchStatus(batchId)));
    }

    @GetMapping("/{batchId}/payments")
    public ResponseEntity<BatchPaymentsResponse> getBatchPayments(@PathVariable Long batchId) {
        return ResponseEntity.ok(BatchPaymentsResponse.from(batchId, paymentBatchService.getBatchPayments(batchId)));
    }
}
