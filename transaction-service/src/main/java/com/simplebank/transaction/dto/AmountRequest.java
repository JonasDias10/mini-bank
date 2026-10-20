package com.simplebank.transaction.dto;

import java.math.BigDecimal;

// Body sent to account-service credit/debit endpoints.
public record AmountRequest(BigDecimal amount) {
}
