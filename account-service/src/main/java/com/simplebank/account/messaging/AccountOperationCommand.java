package com.simplebank.account.messaging;

import com.simplebank.account.model.OperationType;

import java.math.BigDecimal;
import java.util.UUID;

// Sent by transaction-service. operationId is unique per command and makes redelivery safe.
public record AccountOperationCommand(UUID operationId, Long accountId, OperationType type, BigDecimal amount) {

    public boolean hasValidAmount() {
        return amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 2;
    }
}
