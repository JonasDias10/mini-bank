package com.simplebank.transaction.contract;

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
import com.simplebank.transaction.messaging.AccountOperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// transaction-service states which results it understands. The generated pact file (in /pacts) is then
// verified against the real messages account-service produces.
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "account-service", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class AccountOperationResultConsumerPactTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Pact(consumer = "transaction-service")
    MessagePact successfulResult(MessagePactBuilder builder) {
        return builder.expectsToReceive("a successful operation result")
                .withContent(new PactDslJsonBody()
                        .uuid("operationId")
                        .booleanValue("success", true))
                .toPact();
    }

    @Pact(consumer = "transaction-service")
    MessagePact failedResult(MessagePactBuilder builder) {
        return builder.expectsToReceive("a failed operation result")
                .withContent(new PactDslJsonBody()
                        .uuid("operationId")
                        .booleanValue("success", false)
                        .stringType("failureReason", "Insufficient funds in account 1000000000"))
                .toPact();
    }

    private AccountOperationResult read(List<Message> messages) throws IOException {
        return objectMapper.readValue(messages.getFirst().contentsAsBytes(), AccountOperationResult.class);
    }

    @Test
    @PactTestFor(pactMethod = "successfulResult")
    void understandsSuccessfulResult(List<Message> messages) throws IOException {
        AccountOperationResult result = read(messages);

        assertThat(result.operationId()).isNotNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    @PactTestFor(pactMethod = "failedResult")
    void understandsFailedResult(List<Message> messages) throws IOException {
        AccountOperationResult result = read(messages);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }
}
