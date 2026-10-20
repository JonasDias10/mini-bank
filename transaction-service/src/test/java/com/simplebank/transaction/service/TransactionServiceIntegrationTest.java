package com.simplebank.transaction.service;

import com.simplebank.transaction.TestcontainersConfiguration;
import com.simplebank.transaction.client.AccountClient;
import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionType;
import com.simplebank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

// Real PostgreSQL via Testcontainers; only the HTTP calls to account-service are mocked.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransactionServiceIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoBean
    private AccountClient accountClient;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
    }

    private List<TransactionType> historyTypes(Long accountId, Pageable pageable) {
        return transactionService.getTransactionsByAccount(accountId, pageable).stream()
                .map(TransactionResponse::type)
                .toList();
    }

    @Test
    void depositIsPersisted() {
        TransactionResponse deposit = transactionService.deposit(new AccountOperationRequest(1L, AMOUNT));

        TransactionResponse stored = transactionService.getTransaction(deposit.id());

        assertThat(stored.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(stored.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(stored.createdAt()).isNotNull();
    }

    @Test
    void historyIncludesBothSidesNewestFirstAndPages() {
        transactionService.deposit(new AccountOperationRequest(1L, AMOUNT));
        transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT));
        transactionService.withdraw(new AccountOperationRequest(2L, AMOUNT));
        transactionService.deposit(new AccountOperationRequest(3L, AMOUNT));

        assertThat(historyTypes(1L, PageRequest.of(0, 20, NEWEST_FIRST)))
                .containsExactly(TransactionType.TRANSFER, TransactionType.DEPOSIT);
        assertThat(historyTypes(2L, PageRequest.of(0, 20, NEWEST_FIRST)))
                .containsExactly(TransactionType.WITHDRAWAL, TransactionType.TRANSFER);
        assertThat(historyTypes(1L, PageRequest.of(1, 1, NEWEST_FIRST)))
                .containsExactly(TransactionType.DEPOSIT);
    }

    @Test
    void failedOperationPersistsNothing() {
        doThrow(new BusinessException("Insufficient funds")).when(accountClient).debit(1L, AMOUNT);

        assertThatThrownBy(() -> transactionService.withdraw(new AccountOperationRequest(1L, AMOUNT)))
                .isInstanceOf(BusinessException.class);

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void databaseRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(Transaction.deposit(1L, BigDecimal.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
