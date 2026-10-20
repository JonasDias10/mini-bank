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

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    private Transaction(TransactionType type, Long sourceAccountId, Long targetAccountId, BigDecimal amount) {
        this.type = type;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
