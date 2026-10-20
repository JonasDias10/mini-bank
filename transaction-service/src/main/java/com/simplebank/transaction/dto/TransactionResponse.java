package com.simplebank.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionStatus;
import com.simplebank.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        Long id,
        TransactionType type,
        TransactionStatus status,
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        String failureReason,
        Instant createdAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getSourceAccountId(),
                transaction.getTargetAccountId(),
                transaction.getAmount(),
                transaction.getFailureReason(),
                transaction.getCreatedAt()
        );
    }
}
