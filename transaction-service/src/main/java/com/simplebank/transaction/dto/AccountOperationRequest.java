package com.simplebank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// Used for both deposits and withdrawals.
public record AccountOperationRequest(
        @NotNull(message = "Account id is required")
        @Positive(message = "Account id must be positive")
        Long accountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
