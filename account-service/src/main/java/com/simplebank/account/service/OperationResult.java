package com.simplebank.account.service;

// Business failures (unknown account, insufficient funds) are a normal outcome, not an exception.
public record OperationResult(boolean success, String failureReason) {

    public static OperationResult succeeded() {
        return new OperationResult(true, null);
    }

    public static OperationResult failed(String reason) {
        return new OperationResult(false, reason);
    }
}
