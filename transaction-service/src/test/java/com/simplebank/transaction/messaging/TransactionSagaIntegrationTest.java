package com.simplebank.transaction.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.transaction.TestcontainersConfiguration;
import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.model.OperationType;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionStatus;
import com.simplebank.transaction.outbox.OutboxMessage;
import com.simplebank.transaction.outbox.OutboxRepository;
import com.simplebank.transaction.repository.TransactionRepository;
import com.simplebank.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

// Runs the saga through real RabbitMQ and PostgreSQL. The test plays the role of account-service:
// it reads commands from the commands queue and publishes results.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransactionSagaIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("40.00");
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    private Transaction load(Long id) {
        return transactionRepository.findById(id).orElseThrow();
    }

    // Commands left over from other tests may still be in the queue, so skip anything not meant for this step.
    private AccountOperationCommand receiveCommandFor(Long transactionId) throws IOException {
        UUID expected = load(transactionId).getCurrentOperationId();
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(MessagingConfig.COMMANDS_QUEUE, 1_000);

            if (message == null) {
                continue;
            }

            AccountOperationCommand command = objectMapper.readValue(message.getBody(), AccountOperationCommand.class);

            if (command.operationId().equals(expected)) {
                return command;
            }
        }

        return fail("No command received for operation " + expected);
    }

    private void reply(AccountOperationCommand command, boolean success, String reason) {
        rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, MessagingConfig.RESULTS_ROUTING_KEY,
                new AccountOperationResult(command.operationId(), success, reason));
    }

    private void awaitStatus(Long transactionId, TransactionStatus expected) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(load(transactionId).getStatus()).isEqualTo(expected));
    }

    private void awaitNextStep(Long transactionId, UUID previousOperation) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> !previousOperation.equals(load(transactionId).getCurrentOperationId()));
    }

    @Test
    void depositCompletesAfterAccountServiceConfirmsCredit() throws IOException {
        Long id = transactionService.deposit(new AccountOperationRequest(1L, AMOUNT)).id();

        AccountOperationCommand credit = receiveCommandFor(id);
        reply(credit, true, null);

        assertThat(credit.type()).isEqualTo(OperationType.CREDIT);
        assertThat(credit.accountId()).isEqualTo(1L);
        awaitStatus(id, TransactionStatus.COMPLETED);
    }

    @Test
    void transferDebitsSourceThenCreditsTarget() throws IOException {
        Long id = transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)).id();

        AccountOperationCommand debit = receiveCommandFor(id);
        reply(debit, true, null);
        awaitNextStep(id, debit.operationId());
        AccountOperationCommand credit = receiveCommandFor(id);
        reply(credit, true, null);

        assertThat(debit.type()).isEqualTo(OperationType.DEBIT);
        assertThat(debit.accountId()).isEqualTo(1L);
        assertThat(credit.type()).isEqualTo(OperationType.CREDIT);
        assertThat(credit.accountId()).isEqualTo(2L);
        awaitStatus(id, TransactionStatus.COMPLETED);
    }

    @Test
    void transferRefundsSourceWhenCreditFails() throws IOException {
        Long id = transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)).id();

        AccountOperationCommand debit = receiveCommandFor(id);
        reply(debit, true, null);
        awaitNextStep(id, debit.operationId());
        AccountOperationCommand credit = receiveCommandFor(id);
        reply(credit, false, "Account not found with id 2");
        awaitNextStep(id, credit.operationId());
        AccountOperationCommand refund = receiveCommandFor(id);
        reply(refund, true, null);

        assertThat(refund.type()).isEqualTo(OperationType.CREDIT);
        assertThat(refund.accountId()).isEqualTo(1L);
        awaitStatus(id, TransactionStatus.FAILED);
        assertThat(load(id).getFailureReason())
                .isEqualTo("Account not found with id 2. Amount refunded to source account");
    }

    @Test
    void duplicateResultDoesNotAdvanceTheSagaTwice() throws IOException {
        Long id = transactionService.withdraw(new AccountOperationRequest(1L, AMOUNT)).id();

        AccountOperationCommand debit = receiveCommandFor(id);
        reply(debit, false, "Insufficient funds in account 1000000000");
        reply(debit, true, null);

        awaitStatus(id, TransactionStatus.FAILED);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(load(id).getStatus()).isEqualTo(TransactionStatus.FAILED));
    }

    @Test
    void outboxMessagesAreMarkedSentAfterPublishing() throws IOException {
        Long id = transactionService.deposit(new AccountOperationRequest(1L, AMOUNT)).id();

        receiveCommandFor(id);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(outboxRepository.findAll())
                .extracting(OutboxMessage::getSentAt)
                .doesNotContainNull());
    }
}
