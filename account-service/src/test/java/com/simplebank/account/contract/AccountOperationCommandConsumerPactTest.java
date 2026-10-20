package com.simplebank.account.contract;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.account.messaging.AccountOperationCommand;
import com.simplebank.account.model.OperationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// account-service states which commands it understands. The generated pact file (in /pacts) is then verified
// against the real messages transaction-service produces.
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "transaction-service", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class AccountOperationCommandConsumerPactTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private static PactDslJsonBody command(String type) {
        return new PactDslJsonBody()
                .uuid("operationId")
                .integerType("accountId", 1L)
                .stringValue("type", type)
                .numberType("amount", 100.00);
    }

    @Pact(consumer = "account-service")
    MessagePact creditCommand(MessagePactBuilder builder) {
        return builder.expectsToReceive("a credit command").withContent(command("CREDIT")).toPact();
    }

    @Pact(consumer = "account-service")
    MessagePact debitCommand(MessagePactBuilder builder) {
        return builder.expectsToReceive("a debit command").withContent(command("DEBIT")).toPact();
    }

    private AccountOperationCommand read(List<Message> messages) throws IOException {
        return objectMapper.readValue(messages.getFirst().contentsAsBytes(), AccountOperationCommand.class);
    }

    @Test
    @PactTestFor(pactMethod = "creditCommand")
    void understandsCreditCommand(List<Message> messages) throws IOException {
        AccountOperationCommand command = read(messages);

        assertThat(command.operationId()).isNotNull();
        assertThat(command.accountId()).isEqualTo(1L);
        assertThat(command.type()).isEqualTo(OperationType.CREDIT);
        assertThat(command.hasValidAmount()).isTrue();
    }

    @Test
    @PactTestFor(pactMethod = "debitCommand")
    void understandsDebitCommand(List<Message> messages) throws IOException {
        assertThat(read(messages).type()).isEqualTo(OperationType.DEBIT);
    }
}
