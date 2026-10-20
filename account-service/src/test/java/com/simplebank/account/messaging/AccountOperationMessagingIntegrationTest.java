package com.simplebank.account.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.account.TestcontainersConfiguration;
import com.simplebank.account.dto.CreateCustomerRequest;
import com.simplebank.account.model.OperationType;
import com.simplebank.account.outbox.OutboxRepository;
import com.simplebank.account.repository.AccountRepository;
import com.simplebank.account.repository.CustomerRepository;
import com.simplebank.account.repository.ProcessedOperationRepository;
import com.simplebank.account.service.AccountService;
import com.simplebank.account.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// End to end inside account-service: command in from RabbitMQ, balance change in PostgreSQL, result out via outbox.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountOperationMessagingIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProcessedOperationRepository processedOperationRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(MessagingConfig.RESULTS_QUEUE, false);
        Long customerId = customerService.createCustomer(new CreateCustomerRequest("Ana Souza", "ana@example.com")).id();
        accountId = accountService.createAccount(customerId).id();
    }

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        processedOperationRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
    }

    private void send(AccountOperationCommand command) {
        rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, MessagingConfig.COMMANDS_ROUTING_KEY, command);
    }

    private AccountOperationResult receiveResult(long timeoutMs) throws IOException {
        Message message = rabbitTemplate.receive(MessagingConfig.RESULTS_QUEUE, timeoutMs);
        return message == null ? null : objectMapper.readValue(message.getBody(), AccountOperationResult.class);
    }

    private AccountOperationCommand command(OperationType type, String amount) {
        return new AccountOperationCommand(UUID.randomUUID(), accountId, type, new BigDecimal(amount));
    }

    @Test
    void creditCommandUpdatesBalanceAndRepliesSuccess() throws IOException {
        AccountOperationCommand credit = command(OperationType.CREDIT, "100.00");

        send(credit);

        assertThat(receiveResult(RECEIVE_TIMEOUT_MS)).isEqualTo(new AccountOperationResult(credit.operationId(), true, null));
        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitWithInsufficientFundsRepliesFailureAndKeepsBalance() throws IOException {
        AccountOperationCommand debit = command(OperationType.DEBIT, "1.00");

        send(debit);

        AccountOperationResult result = receiveResult(RECEIVE_TIMEOUT_MS);
        assertThat(result.operationId()).isEqualTo(debit.operationId());
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).startsWith("Insufficient funds");
        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("0");
    }

    @Test
    void commandForUnknownAccountRepliesFailure() throws IOException {
        AccountOperationCommand credit = new AccountOperationCommand(
                UUID.randomUUID(), 999_999L, OperationType.CREDIT, BigDecimal.TEN);

        send(credit);

        assertThat(receiveResult(RECEIVE_TIMEOUT_MS).failureReason()).isEqualTo("Account not found with id 999999");
    }

    @Test
    void duplicateCommandIsAppliedOnlyOnce() throws IOException {
        AccountOperationCommand credit = command(OperationType.CREDIT, "100.00");

        send(credit);
        send(credit);

        assertThat(receiveResult(RECEIVE_TIMEOUT_MS)).isNotNull();
        assertThat(receiveResult(2_000)).as("no reply for the duplicate").isNull();
        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("100.00");
        assertThat(processedOperationRepository.count()).isEqualTo(1);
    }
}
