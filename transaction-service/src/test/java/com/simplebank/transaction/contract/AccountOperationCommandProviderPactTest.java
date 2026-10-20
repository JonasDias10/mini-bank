package com.simplebank.transaction.contract;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplebank.transaction.messaging.AccountOperationCommand;
import com.simplebank.transaction.model.SagaStep;
import com.simplebank.transaction.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

// Checks that the commands the saga really produces match what account-service expects.
@Provider("transaction-service")
@PactFolder("../pacts")
class AccountOperationCommandProviderPactTest {

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

    private String commandFor(Transaction transaction, SagaStep step) throws JsonProcessingException {
        transaction.moveTo(step, UUID.randomUUID());
        return objectMapper.writeValueAsString(AccountOperationCommand.forCurrentStep(transaction));
    }

    @PactVerifyProvider("a credit command")
    String creditCommand() throws JsonProcessingException {
        return commandFor(Transaction.deposit(1L, new BigDecimal("100.00")), SagaStep.CREDIT_TARGET);
    }

    @PactVerifyProvider("a debit command")
    String debitCommand() throws JsonProcessingException {
        return commandFor(Transaction.withdrawal(1L, new BigDecimal("100.00")), SagaStep.DEBIT_SOURCE);
    }
}
