package com.settlement.rail.transfer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final PaymentOrchestrator paymentOrchestrator;

    @PostMapping
    public ResponseEntity<TransferResponse> initiate(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentOrchestrator.initiate(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getById(id));
    }

    @GetMapping("/utr/{utr}")
    public ResponseEntity<TransferResponse> getByUtr(@PathVariable String utr) {
        return ResponseEntity.ok(transferService.getByUtr(utr));
    }

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getAll(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long fromBankId,
            @RequestParam(required = false) Long toBankId) {
        if (status != null) {
            return ResponseEntity.ok(transferService.getAllByStatus(status));
        }
        if (fromBankId != null) {
            return ResponseEntity.ok(transferService.getAllByFromBank(fromBankId));
        }
        if (toBankId != null) {
            return ResponseEntity.ok(transferService.getAllByToBank(toBankId));
        }
        return ResponseEntity.ok(transferService.getAll());
    }
}
