package com.simplebank.transaction.model;

// Each step is one command sent to account-service.
public enum SagaStep {
    DEBIT_SOURCE(OperationType.DEBIT),
    CREDIT_TARGET(OperationType.CREDIT),
    REFUND_SOURCE(OperationType.CREDIT);

    private final OperationType operationType;

    SagaStep(OperationType operationType) {
        this.operationType = operationType;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public Long accountOf(Transaction transaction) {
        return this == CREDIT_TARGET ? transaction.getTargetAccountId() : transaction.getSourceAccountId();
    }
}
