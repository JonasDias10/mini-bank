package com.simplebank.account.contract;

import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.PactVerifyProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.account.messaging.AccountOperationResult;
import com.simplebank.account.service.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.UUID;

// Checks that the results account-service really publishes match what transaction-service expects.
@Provider("account-service")
@PactFolder("../pacts")
class AccountOperationResultProviderPactTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a successful operation result")
    String successfulResult() throws JsonProcessingException {
        return objectMapper.writeValueAsString(
                AccountOperationResult.from(UUID.randomUUID(), OperationResult.succeeded()));
    }

    @PactVerifyProvider("a failed operation result")
    String failedResult() throws JsonProcessingException {
        return objectMapper.writeValueAsString(AccountOperationResult.from(
                UUID.randomUUID(), OperationResult.failed("Insufficient funds in account 1000000000")));
    }
}
