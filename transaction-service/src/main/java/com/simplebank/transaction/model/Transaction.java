package com.simplebank.transaction.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    // Account money leaves from. Null for deposits.
    private Long sourceAccountId;

    // Account money goes to. Null for withdrawals.
    private Long targetAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    // The step waiting for a reply from account-service. Null once the transaction is settled.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SagaStep step;

    // Id of the command sent for the current step; the reply carries it back.
    @Column(unique = true)
    private UUID currentOperationId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    private Transaction(TransactionType type, Long sourceAccountId, Long targetAccountId, BigDecimal amount) {
        this.type = type;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        // Same scale as the database column, so the 202 response shows 100.00 like later reads do.
        this.amount = amount.setScale(2);
    }

    public static Transaction deposit(Long accountId, BigDecimal amount) {
        return new Transaction(TransactionType.DEPOSIT, null, accountId, amount);
    }

    public static Transaction withdrawal(Long accountId, BigDecimal amount) {
        return new Transaction(TransactionType.WITHDRAWAL, accountId, null, amount);
    }

    public static Transaction transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        return new Transaction(TransactionType.TRANSFER, fromAccountId, toAccountId, amount);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void moveTo(SagaStep nextStep, UUID operationId) {
        step = nextStep;
        currentOperationId = operationId;
    }

    public void complete() {
        status = TransactionStatus.COMPLETED;
        clearStep();
    }

    public void fail(String reason) {
        status = TransactionStatus.FAILED;
        failureReason = reason;
        clearStep();
    }

    // Keeps why a transfer is being reverted while the refund step is still running.
    public void recordFailureReason(String reason) {
        failureReason = reason;
    }

    private void clearStep() {
        step = null;
        currentOperationId = null;
    }

    public Long getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public SagaStep getStep() {
        return step;
    }

    public UUID getCurrentOperationId() {
        return currentOperationId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
