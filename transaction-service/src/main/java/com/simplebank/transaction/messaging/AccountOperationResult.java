package com.simplebank.transaction.messaging;

import java.util.UUID;

// Reply from account-service. failureReason is only set when success is false.
public record AccountOperationResult(UUID operationId, boolean success, String failureReason) {
}
