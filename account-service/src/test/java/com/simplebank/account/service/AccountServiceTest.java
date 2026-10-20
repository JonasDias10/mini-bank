package com.simplebank.account.service;

import com.simplebank.account.dto.AccountResponse;
import com.simplebank.account.dto.BalanceResponse;
import com.simplebank.account.exception.InsufficientFundsException;
import com.simplebank.account.exception.ResourceNotFoundException;
import com.simplebank.account.model.Account;
import com.simplebank.account.model.Customer;
import com.simplebank.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private AccountService accountService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer("Ana Souza", "ana@example.com");
        ReflectionTestUtils.setField(customer, "id", 1L);
    }

    private Account accountWithBalance(String balance) {
        Account account = new Account("1000000000", customer);
        ReflectionTestUtils.setField(account, "id", 10L);
        account.credit(new BigDecimal(balance));
        return account;
    }

    @Test
    void createAccountUsesNumberFromSequence() {
        when(customerService.findCustomer(1L)).thenReturn(customer);
        when(accountRepository.nextAccountNumber()).thenReturn(1000000042L);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createAccount(1L);

        assertThat(response.accountNumber()).isEqualTo("1000000042");
        assertThat(response.balance()).isEqualByComparingTo("0");
        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.customerName()).isEqualTo("Ana Souza");
    }

    @Test
    void createAccountFailsWhenCustomerDoesNotExist() {
        when(customerService.findCustomer(99L))
                .thenThrow(new ResourceNotFoundException("Customer not found with id 99"));

        assertThatThrownBy(() -> accountService.createAccount(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void getAccountThrowsWhenNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found with id 99");
    }

    @Test
    void getBalanceReturnsCurrentBalance() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(accountWithBalance("250.75")));

        BalanceResponse response = accountService.getBalance(10L);

        assertThat(response.accountId()).isEqualTo(10L);
        assertThat(response.accountNumber()).isEqualTo("1000000000");
        assertThat(response.balance()).isEqualByComparingTo("250.75");
    }

    @Test
    void getAccountsByCustomerReturnsAllAccounts() {
        when(customerService.findCustomer(1L)).thenReturn(customer);
        when(accountRepository.findByCustomerIdOrderByIdAsc(1L))
                .thenReturn(List.of(accountWithBalance("10.00"), accountWithBalance("20.00")));

        List<AccountResponse> accounts = accountService.getAccountsByCustomer(1L);

        assertThat(accounts).extracting(account -> account.balance().toPlainString())
                .containsExactly("10.00", "20.00");
    }

    @Test
    void getAccountsByCustomerFailsWhenCustomerDoesNotExist() {
        when(customerService.findCustomer(99L))
                .thenThrow(new ResourceNotFoundException("Customer not found with id 99"));

        assertThatThrownBy(() -> accountService.getAccountsByCustomer(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(accountRepository, never()).findByCustomerIdOrderByIdAsc(any());
    }

    @Test
    void creditLocksAccountAndAddsAmount() {
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(accountWithBalance("100.00")));

        AccountResponse response = accountService.credit(10L, new BigDecimal("50.00"));

        assertThat(response.balance()).isEqualByComparingTo("150.00");
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void creditThrowsWhenAccountNotFound() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.credit(99L, BigDecimal.TEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void debitSubtractsAmount() {
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(accountWithBalance("100.00")));

        AccountResponse response = accountService.debit(10L, new BigDecimal("40.00"));

        assertThat(response.balance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitAllowsWithdrawingTheWholeBalance() {
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(accountWithBalance("100.00")));

        AccountResponse response = accountService.debit(10L, new BigDecimal("100.00"));

        assertThat(response.balance()).isEqualByComparingTo("0");
    }

    @Test
    void debitRejectsAmountAboveBalanceAndKeepsBalance() {
        Account account = accountWithBalance("100.00");
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.debit(10L, new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient funds in account 1000000000");

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitThrowsWhenAccountNotFound() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.debit(99L, BigDecimal.TEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
