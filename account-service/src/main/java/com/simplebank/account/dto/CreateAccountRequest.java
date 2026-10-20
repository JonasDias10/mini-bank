package com.simplebank.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAccountRequest(
        @NotNull(message = "Customer id is required")
        @Positive(message = "Customer id must be positive")
        Long customerId
) {
}
