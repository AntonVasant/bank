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
        return ResponseEntity.status(HttpStatus.CREATED).body(bankService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bankService.getById(id));
    }

    @GetMapping("/code/{bankCode}")
    public ResponseEntity<BankResponse> getByBankCode(@PathVariable String bankCode) {
        return ResponseEntity.ok(bankService.getByBankCode(bankCode));
    }

    @GetMapping
    public ResponseEntity<List<BankResponse>> getAll(
            @RequestParam(required = false) BankStatus status) {
        if (status != null) {
            return ResponseEntity.ok(bankService.getAllByStatus(status));
        }
        return ResponseEntity.ok(bankService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody BankRequest request) {
        return ResponseEntity.ok(bankService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<BankResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(bankService.activate(id));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<BankResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(bankService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bankService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
