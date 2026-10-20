package com.simplebank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "Source account id is required")
        @Positive(message = "Source account id must be positive")
        Long fromAccountId,

        @NotNull(message = "Destination account id is required")
        @Positive(message = "Destination account id must be positive")
        Long toAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
