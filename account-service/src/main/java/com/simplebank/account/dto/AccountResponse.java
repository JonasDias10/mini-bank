package com.simplebank.account.dto;

import com.simplebank.account.model.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        BigDecimal balance,
        Long customerId,
        String customerName,
        Instant createdAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCustomer().getId(),
                account.getCustomer().getFullName(),
                account.getCreatedAt()
        );
    }
}
