package com.settlement.rail.bank;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @PostMapping
    public ResponseEntity<BankResponse> create(@Valid @RequestBody BankRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BankResponse.from(bankService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(BankResponse.from(bankService.getById(id)));
    }

    @GetMapping("/code/{bankCode}")
    public ResponseEntity<BankResponse> getByBankCode(@PathVariable String bankCode) {
        return ResponseEntity.ok(BankResponse.from(bankService.getByBankCode(bankCode)));
    }

    @GetMapping
    public ResponseEntity<List<BankResponse>> getAll(
            @RequestParam(required = false) BankStatus status) {
        List<Bank> banks = status != null ? bankService.getAllByStatus(status) : bankService.getAll();
        return ResponseEntity.ok(banks.stream().map(BankResponse::from).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody BankRequest request) {
        return ResponseEntity.ok(BankResponse.from(bankService.update(id, request)));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<BankResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(BankResponse.from(bankService.activate(id)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<BankResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(BankResponse.from(bankService.deactivate(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bankService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
