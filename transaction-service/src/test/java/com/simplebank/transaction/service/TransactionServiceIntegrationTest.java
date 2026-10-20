package com.simplebank.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.transaction.TestcontainersConfiguration;
import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.messaging.AccountOperationCommand;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionStatus;
import com.simplebank.transaction.model.TransactionType;
import com.simplebank.transaction.outbox.OutboxMessage;
import com.simplebank.transaction.outbox.OutboxRepository;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real PostgreSQL: persistence, the outbox write and DB constraints. The relay is slowed down so the
// outbox rows can be inspected before they are published.
@SpringBootTest(properties = "outbox.relay-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class TransactionServiceIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    private List<TransactionType> historyTypes(Long accountId, Pageable pageable) {
        return transactionService.getTransactionsByAccount(accountId, pageable).stream()
                .map(TransactionResponse::type)
                .toList();
    }

    @Test
    void depositIsSavedAsPendingTogetherWithItsFirstCommand() throws Exception {
        TransactionResponse deposit = transactionService.deposit(new AccountOperationRequest(1L, AMOUNT));

        Transaction stored = transactionRepository.findById(deposit.id()).orElseThrow();
        List<OutboxMessage> outbox = outboxRepository.findAll();
        AccountOperationCommand command = objectMapper.readValue(outbox.getFirst().getPayload(),
                AccountOperationCommand.class);

        assertThat(stored.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(outbox).hasSize(1);
        assertThat(command.operationId()).isEqualTo(stored.getCurrentOperationId());
        assertThat(command.amount()).isEqualByComparingTo(AMOUNT);
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
    void databaseRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(Transaction.deposit(1L, BigDecimal.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
