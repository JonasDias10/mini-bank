package com.simplebank.account.service;

import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.BalanceResponse;
import com.simplebank.account.exception.InsufficientFundsException;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.model.Account;
import com.simplebank.account.model.Customer;
import com.simplebank.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerService customerService;

    public AccountService(AccountRepository accountRepository, CustomerService customerService) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
    }

    @Transactional
    public AccountResponse createAccount(Long customerId) {
        Customer customer = customerService.findCustomer(customerId);
        String accountNumber = String.valueOf(accountRepository.nextAccountNumber());
        Account account = accountRepository.save(new Account(accountNumber, customer));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long id) {
        return AccountResponse.from(findAccount(id));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long id) {
        Account account = findAccount(id);
        return new BalanceResponse(account.getId(), account.getAccountNumber(), account.getBalance());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomer(Long customerId) {
        customerService.findCustomer(customerId);
        return accountRepository.findByCustomerIdOrderByIdAsc(customerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse credit(Long id, BigDecimal amount) {
        Account account = findAccountForUpdate(id);
        account.credit(amount);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse debit(Long id, BigDecimal amount) {
        Account account = findAccountForUpdate(id);

        if (!account.hasEnoughBalance(amount)) {
            throw new InsufficientFundsException("Insufficient funds in account " + account.getAccountNumber());
        }

        account.debit(amount);
        return AccountResponse.from(account);
    }

    private Account findAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> accountNotFound(id));
    }

    private Account findAccountForUpdate(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> accountNotFound(id));
    }

    private ResourceNotFoundException accountNotFound(Long id) {
        return new ResourceNotFoundException("Account not found with id " + id);
    }
}
