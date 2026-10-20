package com.simplebank.account.messaging;

import com.simplebank.account.service.OperationResult;

import java.util.UUID;

// Reply to an AccountOperationCommand, matched by operationId on the transaction-service side.
public record AccountOperationResult(UUID operationId, boolean success, String failureReason) {

    public static AccountOperationResult from(UUID operationId, OperationResult result) {
        return new AccountOperationResult(operationId, result.success(), result.failureReason());
    }
}
