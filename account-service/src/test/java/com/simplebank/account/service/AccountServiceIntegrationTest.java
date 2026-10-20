package com.simplebank.account.service;

import com.simplebank.account.TestcontainersConfiguration;
import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.CreateCustomerRequest;
import com.simplebank.account.dto.CustomerResponse;
import com.simplebank.account.exception.DuplicateResourceException;
import com.simplebank.account.exception.InsufficientFundsException;
import com.simplebank.account.model.Account;
import com.simplebank.account.repository.AccountRepository;
import com.simplebank.account.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs against a real PostgreSQL, so Flyway migrations, constraints and row locks are all exercised.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
        customerRepository.deleteAll();
    }

    private Long createCustomer(String email) {
        return customerService.createCustomer(new CreateCustomerRequest("Ana Souza", email)).id();
    }

    @Test
    void allMigrationsAreApplied() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isPositive();
    }

    @Test
    void createsCustomerAndAccountsWithSequentialTenDigitNumbers() {
        Long customerId = createCustomer("ana@example.com");

        AccountResponse first = accountService.createAccount(customerId);
        AccountResponse second = accountService.createAccount(customerId);

        assertThat(first.accountNumber()).hasSize(10).containsOnlyDigits();
        assertThat(Long.parseLong(second.accountNumber())).isEqualTo(Long.parseLong(first.accountNumber()) + 1);
        assertThat(accountService.getAccountsByCustomer(customerId))
                .extracting(AccountResponse::id)
                .containsExactly(first.id(), second.id());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        createCustomer("ana@example.com");

        assertThatThrownBy(() -> createCustomer("ANA@Example.com"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void persistsCreditsAndDebits() {
        Long accountId = accountService.createAccount(createCustomer("ana@example.com")).id();

        accountService.credit(accountId, new BigDecimal("100.00"));
        accountService.debit(accountId, new BigDecimal("30.50"));

        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("69.50");
    }

    @Test
    void failedDebitDoesNotChangeStoredBalance() {
        Long accountId = accountService.createAccount(createCustomer("ana@example.com")).id();
        accountService.credit(accountId, new BigDecimal("10.00"));

        assertThatThrownBy(() -> accountService.debit(accountId, new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("10.00");
    }

    @Test
    void databaseRejectsNegativeBalance() {
        Long accountId = accountService.createAccount(createCustomer("ana@example.com")).id();
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(BigDecimal.ONE);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentDebitsNeverOverdrawTheAccount() throws Exception {
        Long accountId = accountService.createAccount(createCustomer("ana@example.com")).id();
        accountService.credit(accountId, new BigDecimal("100.00"));

        int attempts = 10;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> debitTwenty = () -> {
            start.await();
            try {
                accountService.debit(accountId, new BigDecimal("20.00"));
                return true;
            } catch (InsufficientFundsException ex) {
                return false;
            }
        };

        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(debitTwenty));
            }
            start.countDown();
        }

        long succeeded = results.stream().filter(future -> {
            try {
                return future.get();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }).count();

        assertThat(succeeded).isEqualTo(5);
        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("0");
    }
}
