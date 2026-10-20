package com.simplebank.account.dto;

import com.simplebank.account.model.Customer;

import java.time.Instant;

public record CustomerResponse(Long id, String fullName, String email, Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getCreatedAt()
        );
    }
}
