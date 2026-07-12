package com.payment.transaction.controller;

import com.payment.transaction.dto.BalanceResponse;
import com.payment.transaction.dto.CreditAccountRequest;
import com.payment.transaction.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * INTERNAL endpoints — for admin / testing use only.
 * Must NOT be exposed to the public internet. Restrict at the API gateway
 * or network level (e.g. allow only from internal CIDR / VPN).
 */
@RestController
@RequestMapping("/internal/admin/accounts")
public class AdminAccountController {

    private final AccountService accountService;

    public AdminAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * POST /internal/admin/accounts/{accountId}/credit
     *
     * Directly adds money to an account balance. No business validation.
     * Body: { "amount": 500.00 }
     */
    @PostMapping("/{accountId}/credit")
    public ResponseEntity<BalanceResponse> creditAccount(
            @PathVariable Long accountId,
            @RequestBody CreditAccountRequest request) {
        return ResponseEntity.ok(accountService.adminCredit(accountId, request.amount()));
    }
}
