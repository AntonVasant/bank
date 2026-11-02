package com.payment.transaction.service;

import com.payment.transaction.dto.CreateAccountRequest;
import com.payment.transaction.entity.Account;
import com.payment.transaction.entity.User;
import com.payment.transaction.repository.AccountRepository;
import com.payment.transaction.repository.UserRepository;
import io.micrometer.core.annotation.Timed;
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

    @Timed("account.create_account")
    public Account createAccount(CreateAccountRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + request.userId()));

        Account account = new Account();
        account.setAccountNumber(request.accountNumber());
        account.setUser(user);
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setCurrency(Account.Currency.USD);
        account.setBalance(BigDecimal.ZERO);

        return accountRepository.save(account);
    }

    @Timed("account.get_account")
    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
        account.getUser().getId(); // force-initialize the lazy association before the transaction closes
        return account;
    }

    @Timed("account.get_balance")
    @Transactional(readOnly = true)
    public Account getBalance(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
    }

    /**
     * INTERNAL / ADMIN ONLY — directly credits an account balance.
     * For testing and back-office top-ups. No business validation applied.
     */
    @Timed("account.admin_credit")
    public Account adminCredit(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }
}
