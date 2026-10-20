package com.simplebank.transaction.messaging;

import com.simplebank.transaction.model.OperationType;
import com.simplebank.transaction.model.SagaStep;
import com.simplebank.transaction.model.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

// Asks account-service to credit or debit an account. operationId is unique per command.
public record AccountOperationCommand(UUID operationId, Long accountId, OperationType type, BigDecimal amount) {

    public static AccountOperationCommand forCurrentStep(Transaction transaction) {
        SagaStep step = transaction.getStep();
        return new AccountOperationCommand(
                transaction.getCurrentOperationId(),
                step.accountOf(transaction),
                step.getOperationType(),
                transaction.getAmount()
        );
    }
}
