package com.payment.transaction.service;

import com.payment.transaction.dto.AccountResponse;
import com.payment.transaction.dto.BalanceResponse;
import com.payment.transaction.dto.CreateAccountRequest;
import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.User;
import com.payment.transaction.repository.AccountRepository;
import com.payment.transaction.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + request.userId()));

        Account account = new Account();
        account.setAccountNumber(request.accountNumber());
        account.setUser(user);
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setCurrency(Account.Currency.USD);
        account.setBalance(BigDecimal.ZERO);

        Account saved = accountRepository.save(account);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
        return new BalanceResponse(
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency().name());
    }

    /**
     * INTERNAL / ADMIN ONLY — directly credits an account balance.
     * For testing and back-office top-ups. No business validation applied.
     */
    public BalanceResponse adminCredit(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
        account.setBalance(account.getBalance().add(amount));
        Account saved = accountRepository.save(account);
        return new BalanceResponse(
                saved.getAccountNumber(),
                saved.getBalance(),
                saved.getCurrency().name());
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getStatus().name(),
                account.getUser().getId());
    }
}
