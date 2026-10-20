package com.simplebank.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_operations")
public class ProcessedOperation {

    @Id
    private UUID operationId;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedOperation() {
    }

    public ProcessedOperation(UUID operationId) {
        this.operationId = operationId;
        this.processedAt = Instant.now();
    }

    public UUID getOperationId() {
        return operationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
