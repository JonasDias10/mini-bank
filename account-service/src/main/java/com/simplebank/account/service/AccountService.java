package com.simplebank.account.service;

import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.BalanceResponse;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.model.Account;
import com.simplebank.account.model.Customer;
import com.simplebank.account.model.OperationType;
import com.simplebank.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
    public OperationResult applyOperation(Long accountId, OperationType type, BigDecimal amount) {
        Optional<Account> found = accountRepository.findByIdForUpdate(accountId);

        if (found.isEmpty()) {
            return OperationResult.failed("Account not found with id " + accountId);
        }

        Account account = found.get();

        if (type == OperationType.DEBIT && !account.hasEnoughBalance(amount)) {
            return OperationResult.failed("Insufficient funds in account " + account.getAccountNumber());
        }

        switch (type) {
            case CREDIT -> account.credit(amount);
            case DEBIT -> account.debit(amount);
        }
        return OperationResult.succeeded();
    }

    private Account findAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id " + id));
    }
}
